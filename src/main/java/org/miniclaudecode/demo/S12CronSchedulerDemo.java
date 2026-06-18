package org.miniclaudecode.demo;

import org.miniclaudecode.background.BackgroundTasks;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.BackgroundAgentLoop;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.cron.CronJob;
import org.miniclaudecode.cron.CronScheduler;
import org.miniclaudecode.cron.CronStore;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.task.TaskService;
import org.miniclaudecode.task.TaskStore;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.CancelCronTool;
import org.miniclaudecode.tool.ClaimTaskTool;
import org.miniclaudecode.tool.CompleteTaskTool;
import org.miniclaudecode.tool.CreateTaskTool;
import org.miniclaudecode.tool.GetTaskTool;
import org.miniclaudecode.tool.ListCronsTool;
import org.miniclaudecode.tool.ListTasksTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.ScheduleCronTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s12 启动入口：cron 定时触发，不需要人推。
 *
 * 启动时加载 durable cron job，注册到 Hutool CronUtil。
 * 到点后在 cron 回调线程中拿 agentLock，注入 [Scheduled] 消息并运行 Agent。
 * 用户输入同样需要拿 agentLock，避免并发 Agent 调用。
 */
public class S12CronSchedulerDemo {

    private static final String SYSTEM_PROMPT = "You are a coding agent. Act, don't explain.\n\n"
            + "Available tools: bash, read_file, write_file, "
            + "create_task, list_tasks, get_task, claim_task, complete_task, "
            + "schedule_cron, list_crons, cancel_cron.\n\n"
            + "The bash tool accepts an optional run_in_background parameter. "
            + "Set it to true for slow commands like install, build, test, deploy.\n\n"
            + "Working directory: " + System.getProperty("user.dir");

    public static void main(String[] args) {
        File workdir = new File(".");
        TaskService taskService = new TaskService(new TaskStore(workdir));
        BackgroundTasks backgroundTasks = new BackgroundTasks();

        AnthropicConfig config = config(SYSTEM_PROMPT);
        AnthropicLlmClient client = new AnthropicLlmClient(config);

        ToolRegistry registry = new ToolRegistry()
                .register(new BashTool(workdir))
                .register(new ReadFileTool(workdir))
                .register(new WriteFileTool(workdir))
                .register(new CreateTaskTool(taskService))
                .register(new ListTasksTool(taskService))
                .register(new GetTaskTool(taskService))
                .register(new ClaimTaskTool(taskService))
                .register(new CompleteTaskTool(taskService));

        BackgroundAgentLoop loop = new BackgroundAgentLoop(client, registry, backgroundTasks,
                new AgentLoopListener() {
                    @Override
                    public void beforeToolUse(ToolUseBlock toolUse) {
                        System.out.println("Tool> " + toolUse.getName()
                                + " " + toolUse.getInput());
                    }

                    @Override
                    public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
                        System.out.println("ToolResult> " + preview(result.getContent()));
                    }
                });

        List<Message> history = new ArrayList<>();
        Object agentLock = new Object();

        // CronScheduler：触发后在 cron 线程中拿锁调用 Agent
        CronStore cronStore = new CronStore(workdir);
        CronScheduler scheduler = new CronScheduler(cronStore, job -> {
            synchronized (agentLock) {
                System.out.println("  [cron inject] " + job.getPrompt());
                history.add(Message.user("[Scheduled] " + job.getPrompt()));
                AssistantMessage answer = loop.run(history);
                printText(answer);
            }
        });

        // 注册 cron 工具（依赖 CronScheduler）
        registry.register(new ScheduleCronTool(scheduler))
                .register(new ListCronsTool(scheduler))
                .register(new CancelCronTool(scheduler));

        // 启动 cron：加载 durable job + 启动 Hutool 调度线程
        scheduler.start();

        System.out.println("s12: Cron Scheduler");
        System.out.println("输入问题，回车发送。输入 q 退出。\n");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("s12 >> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String query = scanner.nextLine();
            if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
                    || "exit".equalsIgnoreCase(query.trim())) {
                break;
            }
            synchronized (agentLock) {
                history.add(Message.user(query));
                AssistantMessage answer = loop.run(history);
                printText(answer);
            }
        }

        scheduler.stop();
    }

    private static void printText(AssistantMessage answer) {
        if (answer == null || answer.getContent() == null) {
            return;
        }
        for (ContentBlock block : answer.getContent()) {
            if (block instanceof TextBlock) {
                System.out.println(((TextBlock) block).getText());
            }
        }
    }

    private static AnthropicConfig config(String systemPrompt) {
        AnthropicConfig config = new AnthropicConfig();
        config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
        config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
        config.setModel(requiredEnv("MODEL_ID"));
        config.setSystemPrompt(systemPrompt);
        return config;
    }

    private static String preview(String content) {
        if (content == null || content.length() <= 800) {
            return content;
        }
        return content.substring(0, 800) + "\n... (" + (content.length() - 800) + " more chars)";
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env: " + name);
        }
        return value;
    }
}
