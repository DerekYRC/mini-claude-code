package org.miniclaudecode.core;

import org.miniclaudecode.compact.CompactionPipeline;
import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * s08 专用 Agent 循环。
 *
 * 这个循环只展示上下文压缩相关的插入点，不继承权限、hook、todo 等无关机制。
 */
public class CompactingAgentLoop {

	private static final int DEFAULT_MAX_TURNS = 20;

	private final LlmClient llmClient;

	private final ToolRegistry toolRegistry;

	private final CompactionPipeline pipeline;

	private final AgentLoopListener listener;

	private final int maxTurns;

	public CompactingAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, CompactionPipeline pipeline) {
		this(llmClient, toolRegistry, pipeline, new AgentLoopListener() {
		}, DEFAULT_MAX_TURNS);
	}

	public CompactingAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, CompactionPipeline pipeline,
			AgentLoopListener listener) {
		this(llmClient, toolRegistry, pipeline, listener, DEFAULT_MAX_TURNS);
	}

	public CompactingAgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, CompactionPipeline pipeline,
			AgentLoopListener listener, int maxTurns) {
		this.llmClient = llmClient;
		this.toolRegistry = toolRegistry;
		this.pipeline = pipeline;
		this.listener = listener;
		this.maxTurns = maxTurns;
	}

	public AssistantMessage run(String prompt) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.user(prompt));
		return run(messages);
	}

	public AssistantMessage run(List<Message> messages) {
		int reactiveRetries = 0;
		for (int turn = 0; turn < maxTurns; turn++) {
			// LLM 前先运行便宜压缩，只有最后一层才会额外调用模型生成摘要。
			pipeline.beforeLlm(messages);
			AssistantMessage response;
			try {
				response = llmClient.chat(messages, toolRegistry.definitions());
			}
			catch (IllegalStateException e) {
				if (isPromptTooLong(e) && reactiveRetries < 1) {
					replaceAll(messages, pipeline.reactiveCompact(messages));
					reactiveRetries++;
					continue;
				}
				throw e;
			}

			listener.onAssistantMessage(response);
			if (hasCompactToolUse(response)) {
				handleCompactToolUses(messages, response);
				continue;
			}

			messages.add(Message.assistant(response.getContent()));
			List<ToolResultBlock> toolResults = executeToolUses(response);
			if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
				listener.onStop(response);
				return response;
			}
			messages.add(Message.toolResults(toolResults));
		}
		throw new IllegalStateException("Compacting agent loop reached max turns: " + maxTurns);
	}

	private boolean hasCompactToolUse(AssistantMessage response) {
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock && "compact".equals(((ToolUseBlock) block).getName())) {
				return true;
			}
		}
		return false;
	}

	private void handleCompactToolUses(List<Message> messages, AssistantMessage response) {
		String focus = "manual compact";
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock && "compact".equals(((ToolUseBlock) block).getName())) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				if (toolUse.getInput() != null && toolUse.getInput().getString("focus") != null) {
					focus = toolUse.getInput().getString("focus");
				}
				break;
			}
		}
		replaceAll(messages, pipeline.compactHistory(messages, focus));
		messages.add(Message.assistant(response.getContent()));

		List<ToolResultBlock> results = new ArrayList<>();
		for (ContentBlock block : response.getContent()) {
			if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				listener.beforeToolUse(toolUse);
				ToolResult result;
				if ("compact".equals(toolUse.getName())) {
					result = new ToolResult("[Compacted. History summarized.]");
				}
				else {
					result = executeTool(toolUse);
				}
				listener.afterToolUse(toolUse, result);
				results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
			}
		}
		messages.add(Message.toolResults(results));
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
		Tool tool = toolRegistry.find(toolUse.getName());
		if (tool == null) {
			return new ToolResult("Unknown tool: " + toolUse.getName());
		}
		return tool.execute(toolUse.getInput());
	}

	private boolean isPromptTooLong(IllegalStateException e) {
		String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
		return (message.contains("prompt") && message.contains("long"))
				|| message.contains("prompt_is_too_long")
				|| message.contains("context") && message.contains("long");
	}

	private void replaceAll(List<Message> messages, List<Message> next) {
		messages.clear();
		messages.addAll(next);
	}
}
