package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.core.AgentLoop;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.LlmClient;

/**
 * 子 Agent 工具。
 *
 * 父 Agent 调用 task 时，这个工具会创建一段全新的 messages，
 * 让子 Agent 在干净上下文中完成 description 描述的任务。
 */
public class TaskTool implements Tool {

	private final LlmClient subagentClient;

	private final ToolRegistry subagentTools;

	public TaskTool(LlmClient subagentClient, ToolRegistry subagentTools) {
		this.subagentClient = subagentClient;
		this.subagentTools = subagentTools;
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("description", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Subtask for a fresh-context subagent"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("description"));
		return new ToolDefinition("task",
				"Launch a subagent for a complex subtask. Returns only the final summary.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String description = input == null ? "" : input.getString("description");
		if (description == null || description.isBlank()) {
			return new ToolResult("Error: No description provided");
		}

		System.out.println("[Subagent spawned]");
		// 子 Agent 不复用父 Agent 的 history，这是本章“干净上下文”的核心。
		AgentLoop subLoop = new AgentLoop(subagentClient, subagentTools, new AgentLoopListener() {
			@Override
			public void beforeToolUse(ToolUseBlock toolUse) {
				System.out.println("  [sub] Tool> " + toolUse.getName() + " " + toolUse.getInput());
			}
		}, 30);
		AssistantMessage answer = subLoop.run(description);
		System.out.println("[Subagent done]");
		// 父 Agent 只拿到最终摘要，不拿到子 Agent 的完整中间消息。
		return new ToolResult(extractText(answer));
	}

	private String extractText(AssistantMessage message) {
		StringBuilder builder = new StringBuilder();
		for (ContentBlock block : message.getContent()) {
			if (block instanceof TextBlock) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(((TextBlock) block).getText());
			}
		}
		if (builder.length() == 0) {
			return "Subagent finished without text summary.";
		}
		return builder.toString();
	}
}
