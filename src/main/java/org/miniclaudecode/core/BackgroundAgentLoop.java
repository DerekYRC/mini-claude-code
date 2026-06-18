package org.miniclaudecode.core;

import org.miniclaudecode.background.BackgroundDecider;
import org.miniclaudecode.background.BackgroundTasks;
import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * s11 专用 Agent 循环。
 *
 * 在工具执行前判断是否应走后台：模型显式请求 run_in_background 或命令含慢操作关键词时，
 * 启动 daemon 线程执行，先回占位 tool_result 让 Agent 继续思考；
 * 后台完成后以 &lt;task_notification&gt; 格式注入下轮对话。
 *
 * 不继承权限、hook、todo、compact、memory 等无关机制。
 */
public class BackgroundAgentLoop {

    private static final int DEFAULT_MAX_TURNS = 20;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final BackgroundTasks backgroundTasks;
    private final AgentLoopListener listener;
    private final int maxTurns;

    public BackgroundAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry,
            BackgroundTasks backgroundTasks) {
        this(llmClient, toolRegistry, backgroundTasks, new AgentLoopListener() {
        }, DEFAULT_MAX_TURNS);
    }

    public BackgroundAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry,
            BackgroundTasks backgroundTasks, AgentLoopListener listener) {
        this(llmClient, toolRegistry, backgroundTasks, listener, DEFAULT_MAX_TURNS);
    }

    public BackgroundAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry,
            BackgroundTasks backgroundTasks, AgentLoopListener listener, int maxTurns) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.backgroundTasks = backgroundTasks;
        this.listener = listener;
        this.maxTurns = maxTurns;
    }

    public AssistantMessage run(String prompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user(prompt));
        return run(messages);
    }

    public AssistantMessage run(List<Message> messages) {
        for (int turn = 0; turn < maxTurns; turn++) {
            // 每轮 LLM 调用前，先注入上轮后台完成通知
            injectBackgroundNotifications(messages);

            AssistantMessage response = llmClient.chat(messages, toolRegistry.definitions());
            listener.onAssistantMessage(response);
            messages.add(Message.assistant(response.getContent()));

            List<ToolResultBlock> toolResults = executeToolUses(response);
            if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
                listener.onStop(response);
                return response;
            }
            messages.add(Message.toolResults(toolResults));
        }
        throw new IllegalStateException("Background agent loop reached max turns: " + maxTurns);
    }

    /**
     * 执行 assistant 消息中的 tool_use block。
     *
     * 核心分派逻辑：慢操作 → 后台 daemon 线程 + 占位 tool_result；快操作 → 同步执行。
     */
    private List<ToolResultBlock> executeToolUses(AssistantMessage response) {
        List<ToolResultBlock> results = new ArrayList<>();
        for (ContentBlock block : response.getContent()) {
            if (block instanceof ToolUseBlock) {
                ToolUseBlock toolUse = (ToolUseBlock) block;
                listener.beforeToolUse(toolUse);

                ToolResult result;
                if (BackgroundDecider.shouldRunBackground(toolUse.getName(), toolUse.getInput())) {
                    String bgId = backgroundTasks.start(toolUse, toolRegistry);
                    String command = toolUse.getInput() != null
                            ? toolUse.getInput().getString("command") : "";
                    result = new ToolResult(
                            "[Background task " + bgId + " started] "
                            + "Command: " + command + ". "
                            + "Result will be available when complete.");
                } else {
                    result = executeTool(toolUse);
                }

                listener.afterToolUse(toolUse, result);
                results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
            }
        }
        return results;
    }

    /**
     * 同步执行工具。
     */
    private ToolResult executeTool(ToolUseBlock toolUse) {
        Tool tool = toolRegistry.find(toolUse.getName());
        if (tool == null) {
            return new ToolResult("Unknown tool: " + toolUse.getName());
        }
        return tool.execute(toolUse.getInput());
    }

    /**
     * 收集后台完成通知并注入到消息历史。
     *
     * 通知作为独立 user 消息追加，下一轮 LLM 调用时就能看到后台任务的结果。
     */
    private void injectBackgroundNotifications(List<Message> messages) {
        List<String> notifications = backgroundTasks.collectNotifications();
        if (!notifications.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String notif : notifications) {
                sb.append(notif).append("\n");
            }
            messages.add(Message.user(sb.toString().trim()));
            System.out.println("  [inject] " + notifications.size()
                    + " background notification(s)");
        }
    }
}
