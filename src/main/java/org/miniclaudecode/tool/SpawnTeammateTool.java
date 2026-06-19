package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolResultBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.protocol.ProtocolService;
import org.miniclaudecode.team.MessageBus;
import org.miniclaudecode.team.TeamMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 启动一个队友 Agent 线程。
 *
 * 队友只存在于当前 Java 进程；activeTeammates 对齐参考项目的 active_teammates dict。
 */
public class SpawnTeammateTool implements Tool {

    private static final int MAX_TEAMMATE_TURNS = 10;

    private static final int INBOX_NONE = 0;

    private static final int INBOX_CONTINUE = 1;

    private static final int INBOX_SHUTDOWN = 2;

    private final File workdir;

    private final MessageBus bus;

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final String promptTemplate;

    private final ProtocolService protocol;

    private final Set<String> activeTeammates = ConcurrentHashMap.newKeySet();

    public SpawnTeammateTool(File workdir, MessageBus bus, String baseUrl,
            String apiKey, String model, String promptTemplate) {
        this(workdir, bus, baseUrl, apiKey, model, promptTemplate, null);
    }

    public SpawnTeammateTool(File workdir, MessageBus bus, String baseUrl,
            String apiKey, String model, String promptTemplate, ProtocolService protocol) {
        this.workdir = workdir;
        this.bus = bus;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.promptTemplate = promptTemplate;
        this.protocol = protocol;
    }

    /*
     * {
     *   "name": "spawn_teammate",
     *   "description": "Spawn a teammate agent in a background thread.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {
     *       "name": {"type": "string"},
     *       "role": {"type": "string"},
     *       "prompt": {"type": "string"}
     *     },
     *     "required": ["name", "role", "prompt"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("name", new JSONObject().fluentPut("type", "string"))
                .fluentPut("role", new JSONObject().fluentPut("type", "string"))
                .fluentPut("prompt", new JSONObject().fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray()
                        .fluentAdd("name").fluentAdd("role").fluentAdd("prompt"));
        return new ToolDefinition("spawn_teammate",
                "Spawn a teammate agent in a background thread.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        String name = input == null ? "" : input.getString("name");
        String role = input == null ? "" : input.getString("role");
        String prompt = input == null ? "" : input.getString("prompt");
        if (name == null || name.isBlank()) {
            return new ToolResult("Error: name is required");
        }
        if (role == null || role.isBlank()) {
            return new ToolResult("Error: role is required");
        }
        if (prompt == null || prompt.isBlank()) {
            return new ToolResult("Error: prompt is required");
        }
        String finalName = name.trim();
        if (!activeTeammates.add(finalName)) {
            return new ToolResult("Teammate '" + finalName + "' already exists");
        }
        Thread thread = new Thread(() -> runTeammate(finalName, role.trim(), prompt.trim()),
                "mini-claude-code-teammate-" + finalName);
        thread.setDaemon(true);
        thread.start();
        System.out.println("  [teammate] " + finalName + " spawned as " + role.trim());
        return new ToolResult("Teammate '" + finalName + "' spawned as " + role.trim());
    }

    private void runTeammate(String name, String role, String prompt) {
        try {
            ToolRegistry registry = new ToolRegistry()
                    .register(new BashTool(workdir))
                    .register(new ReadFileTool(workdir))
                    .register(new WriteFileTool(workdir))
                    .register(new SendMessageTool(bus, name));
            if (protocol != null) {
                registry.register(new SubmitPlanTool(protocol, name));
            }
            LlmClient client = new AnthropicLlmClient(config(String.format(promptTemplate,
                    name, role, workdir.getAbsolutePath())));
            AssistantMessage answer = protocol == null
                    ? runFixedTurnLoop(name, prompt, client, registry)
                    : runProtocolLoop(name, prompt, client, registry);
            String summary = extractText(answer);
            if (summary.isBlank()) {
                summary = "Done.";
            }
            bus.send(name, "lead", summary, "result");
            System.out.println("  [teammate] " + name + " finished");
        }
        catch (RuntimeException e) {
            bus.send(name, "lead", "Error: " + e.getMessage(), "result");
        }
        finally {
            activeTeammates.remove(name);
        }
    }

    private AssistantMessage runFixedTurnLoop(String name, String prompt,
            LlmClient client, ToolRegistry registry) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user(prompt));
        AssistantMessage lastResponse = null;
        for (int turn = 0; turn < MAX_TEAMMATE_TURNS; turn++) {
            // 队友不暴露 check_inbox；harness 每轮自动注入 inbox，
            // 这样 Lead 的后续消息不会依赖模型主动想起检查邮箱。
            injectTeammateInbox(name, messages);

            AssistantMessage response = client.chat(messages, registry.definitions());
            lastResponse = response;
            messages.add(Message.assistant(response.getContent()));

            List<ToolResultBlock> toolResults = executeToolUses(response, registry);
            if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
                return response;
            }
            messages.add(Message.toolResults(toolResults));
        }
        return lastResponse;
    }

    private AssistantMessage runProtocolLoop(String name, String prompt,
            LlmClient client, ToolRegistry registry) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user(prompt));
        AssistantMessage lastResponse = null;

        for (int turn = 0; turn < MAX_TEAMMATE_TURNS; turn++) {
            int inboxAction = injectTeammateInbox(name, messages);
            if (inboxAction == INBOX_SHUTDOWN) {
                return lastResponse;
            }

            AssistantMessage response = client.chat(messages, registry.definitions());
            lastResponse = response;
            messages.add(Message.assistant(response.getContent()));

            List<ToolResultBlock> toolResults = executeToolUses(response, registry);
            if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
                // s14 的关键差异：队友空闲后等待 inbox，而不是直接退出。
                int idleAction = idleUntilMessage(name, messages);
                if (idleAction == INBOX_SHUTDOWN) {
                    return response;
                }
                continue;
            }
            messages.add(Message.toolResults(toolResults));
        }
        return lastResponse;
    }

    private int injectTeammateInbox(String name, List<Message> messages) {
        List<TeamMessage> inbox = bus.readInbox(name);
        if (inbox.isEmpty()) {
            return INBOX_NONE;
        }
        System.out.println("  [teammate inbox] " + name + ": "
                + inbox.size() + " message(s)");

        List<TeamMessage> normalMessages = new ArrayList<>();
        for (TeamMessage message : inbox) {
            if (protocol != null && protocol.isProtocolMessage(message)) {
                boolean shouldStop = protocol.handleTeammateProtocolMessage(name, message, messages);
                if (shouldStop) {
                    return INBOX_SHUTDOWN;
                }
            } else {
                normalMessages.add(message);
            }
        }
        if (!normalMessages.isEmpty()) {
            messages.add(Message.user("<inbox>\n"
                    + bus.formatInbox(normalMessages) + "\n</inbox>"));
            return INBOX_CONTINUE;
        }
        return INBOX_NONE;
    }

    private int idleUntilMessage(String name, List<Message> messages) {
        while (true) {
            sleepOneSecond();
            int inboxAction = injectTeammateInbox(name, messages);
            if (inboxAction != INBOX_NONE) {
                return inboxAction;
            }
        }
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Teammate interrupted", e);
        }
    }

    private List<ToolResultBlock> executeToolUses(AssistantMessage response,
            ToolRegistry registry) {
        List<ToolResultBlock> results = new ArrayList<>();
        if (response == null || response.getContent() == null) {
            return results;
        }
        for (ContentBlock block : response.getContent()) {
            if (block instanceof ToolUseBlock) {
                ToolUseBlock toolUse = (ToolUseBlock) block;
                Tool tool = registry.find(toolUse.getName());
                ToolResult result = tool == null
                        ? new ToolResult("Unknown tool: " + toolUse.getName())
                        : tool.execute(toolUse.getInput());
                System.out.println("  [teammate tool] " + toolUse.getName()
                        + " -> " + preview(result.getContent()));
                results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
            }
        }
        return results;
    }

    private AnthropicConfig config(String systemPrompt) {
        AnthropicConfig config = new AnthropicConfig();
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        config.setModel(model);
        config.setSystemPrompt(systemPrompt);
        return config;
    }

    private String extractText(AssistantMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof TextBlock) {
                sb.append(((TextBlock) block).getText()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String preview(String content) {
        if (content == null || content.length() <= 120) {
            return content;
        }
        return content.substring(0, 120) + "...";
    }
}
