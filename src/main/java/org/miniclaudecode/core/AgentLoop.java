package org.miniclaudecode.core;

import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolDefinition;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * s02 的 Agent 循环。
 *
 * 循环仍然只负责 LLM 和工具结果之间的来回传递；
 * 新增工具的分发细节交给 ToolRegistry。
 */
public class AgentLoop {

	private final LlmClient llmClient;

	private final ToolRegistry toolRegistry;

	private final AgentLoopListener listener;

	public AgentLoop(LlmClient llmClient, List<Tool> tools) {
		this(llmClient, tools, new AgentLoopListener() {
		});
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener) {
		this.llmClient = llmClient;
		this.listener = listener;
		this.toolRegistry = new ToolRegistry();
		for (Tool tool : tools) {
			this.toolRegistry.register(tool);
		}
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry) {
		this(llmClient, toolRegistry, new AgentLoopListener() {
		});
	}

	public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentLoopListener listener) {
		this.llmClient = llmClient;
		this.toolRegistry = toolRegistry;
		this.listener = listener;
	}

	public AssistantMessage run(String prompt) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.user(prompt));
		return run(messages);
	}

	public AssistantMessage run(List<Message> messages) {
		for (int turn = 0; turn < 20; turn++) {
			// 主循环不关心工具数量，只把当前 history 和工具定义发给模型。
			AssistantMessage response = llmClient.chat(messages, toolDefinitions());
			listener.onAssistantMessage(response);
			// assistant 消息写回 history，下一轮模型才能看到自己刚才的 tool_use。
			messages.add(Message.assistant(response.getContent()));

			List<ToolResultBlock> toolResults = executeToolUses(response);
			if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
				listener.onStop(response);
				return response;
			}

			// tool_result 以 user role 回传，这是 Anthropic Messages 工具协议的要求。
			messages.add(Message.toolResults(toolResults));
		}

		throw new IllegalStateException("Agent loop reached max turns");
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
				ToolResult result = executeTool(toolUse);
				listener.afterToolUse(toolUse, result);
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
}
