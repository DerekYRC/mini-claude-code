package org.miniclaudecode.demo;

import org.miniclaudecode.background.BackgroundTasks;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.BackgroundAgentLoop;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.task.TaskService;
import org.miniclaudecode.task.TaskStore;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.ClaimTaskTool;
import org.miniclaudecode.tool.CompleteTaskTool;
import org.miniclaudecode.tool.CreateTaskTool;
import org.miniclaudecode.tool.GetTaskTool;
import org.miniclaudecode.tool.ListTasksTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s11 启动入口：后台执行慢操作，Agent 继续思考。
 */
public class S11BackgroundTasksDemo {

    private static final String SYSTEM_PROMPT = "You are a coding agent. Act, don't explain.\n\n"
            + "Available tools: bash, read_file, write_file, "
            + "create_task, list_tasks, get_task, claim_task, complete_task.\n\n"
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

        System.out.println("s11: Background Tasks");
        System.out.println("输入问题，回车发送。输入 q 退出。\n");

        List<Message> history = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("s11 >> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String query = scanner.nextLine();
            if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
                    || "exit".equalsIgnoreCase(query.trim())) {
                break;
            }
            history.add(Message.user(query));
            AssistantMessage answer = loop.run(history);
            if (answer.getContent() != null) {
                for (ContentBlock block : answer.getContent()) {
                    if (block instanceof TextBlock) {
                        System.out.println(((TextBlock) block).getText());
                    }
                }
            }
            System.out.println();
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
