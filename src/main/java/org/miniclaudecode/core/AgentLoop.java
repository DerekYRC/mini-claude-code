package org.miniclaudecode.core;

import org.miniclaudecode.hook.HookContext;
import org.miniclaudecode.hook.HookDecision;
import org.miniclaudecode.hook.HookEvent;
import org.miniclaudecode.hook.HookManager;
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
 * s01 的最小 Agent 循环。
 *
 * 这个类只做三件事：把 history 发给模型、执行模型请求的工具、
 * 再把工具结果塞回 history 交给模型继续思考。
 */
public class AgentLoop {

	private static final int DEFAULT_MAX_TURNS = 20;

	private final LlmClient llmClient;

	private final ToolRegistry toolRegistry;

	private final AgentLoopListener listener;

	private final PermissionManager permissionManager;

	private final HookManager hookManager;

	private final int maxTurns;

	public AgentLoop(LlmClient llmClient, List<Tool> tools) {
		this(llmClient, tools, new AgentLoopListener() {
		}, null);
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener) {
		this(llmClient, tools, listener, null);
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener,
			PermissionManager permissionManager) {
		this(llmClient, toRegistry(tools), listener, permissionManager, null, DEFAULT_MAX_TURNS);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry) {
		this(llmClient, toolRegistry, new AgentLoopListener() {
		}, (PermissionManager) null);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener) {
		this(llmClient, toolRegistry, listener, (PermissionManager) null);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener,
			PermissionManager permissionManager) {
		this(llmClient, toolRegistry, listener, permissionManager, null, DEFAULT_MAX_TURNS);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener,
			HookManager hookManager) {
		this(llmClient, toolRegistry, listener, null, hookManager, DEFAULT_MAX_TURNS);
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener,
			int maxTurns) {
		this(llmClient, toolRegistry, listener, null, null, maxTurns);
	}

	private AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener,
			PermissionManager permissionManager, HookManager hookManager, int maxTurns) {
		this.llmClient = llmClient;
		this.toolRegistry = toolRegistry;
		this.listener = listener;
		this.permissionManager = permissionManager;
		this.hookManager = hookManager;
		this.maxTurns = maxTurns;
	}

	public AssistantMessage run(String prompt) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.user(prompt));
		return run(messages);
	}

	public AssistantMessage run(List<Message> messages) {
		for (int turn = 0; turn < maxTurns; turn++) {
			// 一轮 Agent 循环：LLM -> tool_use -> tool_result -> 下一轮 LLM。
			AssistantMessage response = llmClient.chat(messages, toolDefinitions());
			listener.onAssistantMessage(response);
			// assistant 消息必须写回 history，否则下一轮模型不知道自己刚才请求了哪个工具。
			messages.add(Message.assistant(response.getContent()));

			List<ToolResultBlock> toolResults = executeToolUses(response);
			if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
				triggerStopHooks(messages);
				listener.onStop(response);
				return response;
			}

			// tool_result 以 user role 回传，这是 Anthropic Messages 工具协议的要求。
			messages.add(Message.toolResults(toolResults));
		}

		throw new IllegalStateException("Agent loop reached max turns: " + maxTurns);
	}

	private static ToolRegistry toRegistry(List<Tool> tools) {
		ToolRegistry registry = new ToolRegistry();
		for (Tool tool : tools) {
			registry.register(tool);
		}
		return registry;
	}

	private List<ToolDefinition> toolDefinitions() {
		return toolRegistry.definitions();
	}

	/**
	 * 执行 assistant 消息中的所有 tool_use，并把结果转成 tool_result block。
	 */
	private List<ToolResultBlock> executeToolUses(AssistantMessage response) {
		List<ToolResultBlock> results = new ArrayList<>();
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				listener.beforeToolUse(toolUse);
				PermissionDecision decision = checkPermission(toolUse);
				if (!decision.isAllowed()) {
					results.add(new ToolResultBlock(toolUse.getId(), decision.getMessage()));
					continue;
				}
				HookDecision hookDecision = triggerPreToolUseHooks(toolUse);
				if (hookDecision.isBlocked()) {
					results.add(new ToolResultBlock(toolUse.getId(), hookDecision.getMessage()));
					continue;
				}
				ToolResult result = executeTool(toolUse);
				listener.afterToolUse(toolUse, result);
				triggerPostToolUseHooks(toolUse, result);
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

	private HookDecision triggerPreToolUseHooks(ToolUseBlock toolUse) {
		if (hookManager == null) {
			return HookDecision.pass();
		}
		HookContext context = new HookContext(HookEvent.PRE_TOOL_USE);
		context.setToolUse(toolUse);
		return hookManager.trigger(HookEvent.PRE_TOOL_USE, context);
	}

	private void triggerPostToolUseHooks(ToolUseBlock toolUse, ToolResult result) {
		if (hookManager == null) {
			return;
		}
		HookContext context = new HookContext(HookEvent.POST_TOOL_USE);
		context.setToolUse(toolUse);
		context.setToolResult(result);
		hookManager.trigger(HookEvent.POST_TOOL_USE, context);
	}

	private void triggerStopHooks(List<Message> messages) {
		if (hookManager == null) {
			return;
		}
		HookContext context = new HookContext(HookEvent.STOP);
		context.setMessages(messages);
		hookManager.trigger(HookEvent.STOP, context);
	}
}
