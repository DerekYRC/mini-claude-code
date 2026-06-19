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
import org.miniclaudecode.protocol.ProtocolService;
import org.miniclaudecode.task.TaskService;
import org.miniclaudecode.task.TaskStore;
import org.miniclaudecode.team.MessageBus;
import org.miniclaudecode.team.TeamMessage;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.ClaimTaskTool;
import org.miniclaudecode.tool.CompleteTaskTool;
import org.miniclaudecode.tool.CreateTaskTool;
import org.miniclaudecode.tool.GetTaskTool;
import org.miniclaudecode.tool.ListTasksTool;
import org.miniclaudecode.tool.ProtocolCheckInboxTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.RequestPlanTool;
import org.miniclaudecode.tool.RequestShutdownTool;
import org.miniclaudecode.tool.ReviewPlanTool;
import org.miniclaudecode.tool.SendMessageTool;
import org.miniclaudecode.tool.SpawnTeammateTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s14 启动入口：在团队邮箱上增加 request_id 协议。
 */
public class S14TeamProtocolsDemo {

    private static final String LEAD_SYSTEM_PROMPT = "You are a coding agent. Act, don't explain.\n\n"
            + "Available tools: bash, read_file, write_file, "
            + "get_task, create_task, list_tasks, claim_task, complete_task, "
            + "spawn_teammate, send_message, check_inbox, "
            + "request_shutdown, request_plan, review_plan.\n\n"
            + "Working directory: " + System.getProperty("user.dir");

    private static final String TEAMMATE_SYSTEM_PROMPT_TEMPLATE =
            "You are '%s', a %s. Use tools to complete tasks. "
                    + "Check inbox for protocol messages (shutdown_request, etc).\n\n"
                    + "Working directory: %s";

    public static void main(String[] args) {
        File workdir = new File(".");
        TaskService taskService = new TaskService(new TaskStore(workdir));
        BackgroundTasks backgroundTasks = new BackgroundTasks();
        MessageBus bus = new MessageBus(workdir);
        ProtocolService protocol = new ProtocolService(bus);

        AnthropicConfig config = config(LEAD_SYSTEM_PROMPT);
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

        registry.register(new SpawnTeammateTool(workdir, bus,
                        requiredEnv("ANTHROPIC_BASE_URL"),
                        requiredEnv("ANTHROPIC_API_KEY"),
                        requiredEnv("MODEL_ID"),
                        TEAMMATE_SYSTEM_PROMPT_TEMPLATE,
                        protocol))
                .register(new SendMessageTool(bus, "lead"))
                .register(new ProtocolCheckInboxTool(protocol, bus))
                .register(new RequestShutdownTool(protocol))
                .register(new RequestPlanTool(protocol))
                .register(new ReviewPlanTool(protocol));

        List<Message> history = new ArrayList<>();

        System.out.println("s14: Team Protocols");
        System.out.println("输入问题，回车发送。输入 q 退出。\n");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("s14 >> ");
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
            printText(answer);
            injectLeadInbox(protocol, bus, history);
        }
    }

    private static void injectLeadInbox(ProtocolService protocol, MessageBus bus,
            List<Message> history) {
        List<TeamMessage> inbox = protocol.consumeLeadInbox();
        if (inbox.isEmpty()) {
            return;
        }
        // check_inbox 工具和主循环共用同一个消费入口，协议状态只在一个地方更新。
        history.add(Message.user("[Inbox]\n" + bus.formatInbox(inbox)));
        System.out.println("  [Inbox: " + inbox.size() + " messages injected]");
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
