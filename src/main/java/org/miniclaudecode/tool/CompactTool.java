package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;

/**
 * 暴露给模型的 compact 控制工具。
 *
 * 真正压缩需要改写 messages，所以由 CompactingAgentLoop 特殊处理。
 */
public class CompactTool implements Tool {

	@Override
	/*
	 * {
	 *   "name": "compact",
	 *   "description": "Summarize earlier conversation to free context space.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "focus": {"type": "string"}
	 *     }
	 *   }
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("focus", new JSONObject().fluentPut("type", "string"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties);
		return new ToolDefinition("compact", "Summarize earlier conversation to free context space.", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		return new ToolResult("Compact should be handled by CompactingAgentLoop");
	}
}
