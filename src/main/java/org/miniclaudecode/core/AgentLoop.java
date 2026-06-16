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
 * 聚合版 Agent 循环。
 *
 * s01 展示 LLM -> 工具 -> LLM 的闭环，s02 开始把工具查找交给 ToolRegistry；
 * s03 在工具执行前加入 PermissionManager，s04 开始在固定位置触发 hook。
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
			// 权限不改变模型调用协议；主循环仍然只把 history 和工具定义发给模型。
			AssistantMessage response = llmClient.chat(messages, toolDefinitions());
			listener.onAssistantMessage(response);
			// assistant 消息写回 history，下一轮模型才能看到自己刚才的 tool_use。
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
	 * 执行 assistant 消息中的 tool_use。真正的工具查找由 ToolRegistry 完成。
	 */
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
				// PreToolUse 位于工具真正执行前，可以阻断本次工具调用。
				HookDecision hookDecision = triggerPreToolUseHooks(toolUse);
				if (hookDecision.isBlocked()) {
					results.add(new ToolResultBlock(toolUse.getId(), hookDecision.getMessage()));
					continue;
				}
				ToolResult result = executeTool(toolUse);
				listener.afterToolUse(toolUse, result);
				// PostToolUse 位于工具执行后，适合记录日志或检查输出。
				triggerPostToolUseHooks(toolUse, result);
				results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
			}
		}
		return results;
	}

	private ToolResult executeTool(ToolUseBlock toolUse) {
		// s02 的新增点：按工具名从 dispatch map 找 handler，AgentLoop 不写死具体工具类。
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
		// Stop 位于循环结束时，适合做会话级统计。
		HookContext context = new HookContext(HookEvent.STOP);
		context.setMessages(messages);
		hookManager.trigger(HookEvent.STOP, context);
	}
}
