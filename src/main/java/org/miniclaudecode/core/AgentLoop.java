package org.miniclaudecode.core;

import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.permission.PermissionDecision;
import org.miniclaudecode.permission.PermissionManager;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolDefinition;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * s03 的 Agent 循环。
 *
 * 循环的核心仍然是 LLM -> tool_use -> tool_result；
 * 本章只是在工具执行前多了一道 PermissionManager。
 */
public class AgentLoop {

	private final LlmClient llmClient;

	private final ToolRegistry toolRegistry;

	private final AgentLoopListener listener;

	private final PermissionManager permissionManager;

	public AgentLoop(LlmClient llmClient, List<Tool> tools) {
		this(llmClient, tools, new AgentLoopListener() {
		}, null);
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener) {
		this(llmClient, tools, listener, null);
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener,
			PermissionManager permissionManager) {
		this.llmClient = llmClient;
		this.listener = listener;
		this.permissionManager = permissionManager;
		this.toolRegistry = new ToolRegistry();
		for (Tool tool : tools) {
			this.toolRegistry.register(tool);
		}
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry) {
		this(llmClient, toolRegistry, new AgentLoopListener() {
		}, null);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener) {
		this(llmClient, toolRegistry, listener, null);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener,
			PermissionManager permissionManager) {
		this.llmClient = llmClient;
		this.toolRegistry = toolRegistry;
		this.listener = listener;
		this.permissionManager = permissionManager;
	}

	public AssistantMessage run(String prompt) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.user(prompt));
		return run(messages);
	}

	public AssistantMessage run(List<Message> messages) {
		for (int turn = 0; turn < 20; turn++) {
			// 权限不改变模型调用协议，只改变工具真正执行前的本地决策。
			AssistantMessage response = llmClient.chat(messages, toolDefinitions());
			listener.onAssistantMessage(response);
			messages.add(Message.assistant(response.getContent()));

			List<ToolResultBlock> toolResults = executeToolUses(response);
			if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
				listener.onStop(response);
				return response;
			}

			messages.add(Message.toolResults(toolResults));
		}

		throw new IllegalStateException("Agent loop reached max turns");
	}

	private List<ToolDefinition> toolDefinitions() {
		return toolRegistry.definitions();
	}

	private List<ToolResultBlock> executeToolUses(AssistantMessage response) {
		List<ToolResultBlock> results = new ArrayList<>();
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				listener.beforeToolUse(toolUse);
				PermissionDecision decision = checkPermission(toolUse);
				if (!decision.isAllowed()) {
					// 权限拒绝也要回传 tool_result，让模型知道工具为什么没有执行。
					results.add(new ToolResultBlock(toolUse.getId(), decision.getMessage()));
					continue;
				}
				ToolResult result = executeTool(toolUse);
				listener.afterToolUse(toolUse, result);
				results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
			}
		}
		return results;
	}

	private ToolResult executeTool(ToolUseBlock toolUse) {
		Tool tool = toolRegistry.find(toolUse.getName());
		if (tool == null) {
			return new ToolResult("Unknown tool: " + toolUse.getName());
		}
		return tool.execute(toolUse.getInput());
	}

	private PermissionDecision checkPermission(ToolUseBlock toolUse) {
		if (permissionManager == null) {
			return PermissionDecision.allow();
		}
		return permissionManager.check(toolUse);
	}
}
