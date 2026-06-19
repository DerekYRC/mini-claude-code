package org.miniclaudecode.core;

import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.mcp.McpToolPool;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * s16 专用动态工具池循环。
 *
 * connect_mcp 会改变工具集合，所以每轮 LLM 调用前重新 assemble 工具池。
 */
public class DynamicMcpAgentLoop {

	private static final int DEFAULT_MAX_TURNS = 20;

	private final LlmClient llmClient;

	private final McpToolPool toolPool;

	private final AgentLoopListener listener;

	private final int maxTurns;

	public DynamicMcpAgentLoop(LlmClient llmClient, McpToolPool toolPool,
			AgentLoopListener listener) {
		this(llmClient, toolPool, listener, DEFAULT_MAX_TURNS);
	}

	public DynamicMcpAgentLoop(LlmClient llmClient, McpToolPool toolPool,
			AgentLoopListener listener, int maxTurns) {
		this.llmClient = llmClient;
		this.toolPool = toolPool;
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
			ToolRegistry registry = toolPool.assemble();
			AssistantMessage response = llmClient.chat(messages, registry.definitions());
			listener.onAssistantMessage(response);
			messages.add(Message.assistant(response.getContent()));

			List<ToolResultBlock> toolResults = executeToolUses(response, registry);
			if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
				listener.onStop(response);
				return response;
			}
			messages.add(Message.toolResults(toolResults));
		}
		throw new IllegalStateException("Dynamic MCP agent loop reached max turns: " + maxTurns);
	}

	private List<ToolResultBlock> executeToolUses(AssistantMessage response,
			ToolRegistry registry) {
		List<ToolResultBlock> results = new ArrayList<>();
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				listener.beforeToolUse(toolUse);
				ToolResult result = executeTool(toolUse, registry);
				listener.afterToolUse(toolUse, result);
				results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
			}
		}
		return results;
	}

	private ToolResult executeTool(ToolUseBlock toolUse, ToolRegistry registry) {
		Tool tool = registry.find(toolUse.getName());
		if (tool == null) {
			return new ToolResult("Unknown tool: " + toolUse.getName());
		}
		return tool.execute(toolUse.getInput());
	}
}
