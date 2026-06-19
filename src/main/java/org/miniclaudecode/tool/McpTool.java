package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.mcp.McpClient;
import org.miniclaudecode.mcp.McpToolDefinition;
import org.miniclaudecode.mcp.McpToolName;

/**
 * 把 MCP 工具包装成项目已有的 Tool 接口。
 *
 * 主循环只看到普通 Tool；MCP 来源被隔离在这个 adapter 里。
 */
public class McpTool implements Tool {

	private final McpClient client;

	private final McpToolDefinition mcpDefinition;

	private final String publicName;

	public McpTool(McpClient client, McpToolDefinition mcpDefinition) {
		this.client = client;
		this.mcpDefinition = mcpDefinition;
		this.publicName = McpToolName.prefixed(client.getName(), mcpDefinition.getName());
	}

	/*
	 * {
	 *   "name": "mcp__{server}__{tool}",
	 *   "description": "MCP tool description from server",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {},
	 *     "required": []
	 *   }
	 * }
	 */
	@Override
	public ToolDefinition getDefinition() {
		return new ToolDefinition(publicName,
				mcpDefinition.getDescription(), mcpDefinition.getInputSchema());
	}

	@Override
	public ToolResult execute(JSONObject input) {
		return new ToolResult(client.callTool(mcpDefinition.getName(), input));
	}
}
