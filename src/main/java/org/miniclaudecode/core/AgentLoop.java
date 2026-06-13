package org.miniclaudecode.core;

import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolDefinition;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		List<ToolDefinition> definitions = new ArrayList<>();
		for (Tool tool : tools.values()) {
			definitions.add(tool.getDefinition());
		}
		return definitions;
	}

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
