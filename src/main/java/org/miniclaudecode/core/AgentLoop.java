package org.miniclaudecode.core;

import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolDefinition;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s01 的最小 Agent 循环。
 *
 * 这个类只做三件事：把 history 发给模型、执行模型请求的工具、
 * 再把工具结果塞回 history 交给模型继续思考。
 */
public class AgentLoop {

	private final LlmClient llmClient;

	private final Map<String, Tool> tools = new LinkedHashMap<>();

	private final AgentLoopListener listener;

	public AgentLoop(LlmClient llmClient, List<Tool> tools) {
		this(llmClient, tools, new AgentLoopListener() {
		});
	}

	public AgentLoop(LlmClient llmClient, List<Tool> tools, AgentLoopListener listener) {
		this.llmClient = llmClient;
		this.listener = listener;
		for (Tool tool : tools) {
			// s01 先把工具直接放进 Map；s02 会把这段提取成 ToolRegistry。
			this.tools.put(tool.getDefinition().getName(), tool);
		}
	}

	public AssistantMessage run(String prompt) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.user(prompt));
		return run(messages);
	}

	public AssistantMessage run(List<Message> messages) {
		for (int turn = 0; turn < 20; turn++) {
			// 一轮 Agent 循环：LLM -> tool_use -> tool_result -> 下一轮 LLM。
			AssistantMessage response = llmClient.chat(messages, toolDefinitions());
			listener.onAssistantMessage(response);
			// assistant 消息必须写回 history，否则下一轮模型不知道自己刚才请求了哪个工具。
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
		List<ToolDefinition> definitions = new ArrayList<>();
		for (Tool tool : tools.values()) {
			definitions.add(tool.getDefinition());
		}
		return definitions;
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
				ToolResult result = executeTool(toolUse);
				listener.afterToolUse(toolUse, result);
				results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
			}
		}
		return results;
	}

	private ToolResult executeTool(ToolUseBlock toolUse) {
		Tool tool = tools.get(toolUse.getName());
		if (tool == null) {
			return new ToolResult("Unknown tool: " + toolUse.getName());
		}
		return tool.execute(toolUse.getInput());
	}
}
