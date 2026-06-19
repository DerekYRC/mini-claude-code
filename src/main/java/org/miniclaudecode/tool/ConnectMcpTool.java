package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.mcp.McpManager;

/**
 * 连接 mock MCP server 的入口工具。
 */
public class ConnectMcpTool implements Tool {

	private final McpManager mcpManager;

	public ConnectMcpTool(McpManager mcpManager) {
		this.mcpManager = mcpManager;
	}

	/*
	 * {
	 *   "name": "connect_mcp",
	 *   "description": "Connect to a mock MCP server. Available servers: time, weather.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "name": {"type": "string", "description": "MCP server name"}
	 *     },
	 *     "required": ["name"]
	 *   }
	 * }
	 */
	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("name", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "MCP server name"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("name"));
		return new ToolDefinition("connect_mcp",
				"Connect to a mock MCP server. Available servers: time, weather.", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String name = input == null ? "" : input.getString("name");
		return new ToolResult(mcpManager.connect(name));
	}
}
