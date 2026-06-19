package org.miniclaudecode.mcp;

import com.alibaba.fastjson.JSONObject;

/**
 * mock MCP server 暴露的工具定义。
 *
 * 字段名保留 inputSchema，模拟 MCP tools/list 的返回形态；转换给模型时再变成 input_schema。
 */
public class McpToolDefinition {

	private String name;

	private String description;

	private JSONObject inputSchema;

	private boolean readOnly;

	public McpToolDefinition(String name, String description, JSONObject inputSchema, boolean readOnly) {
		this.name = name;
		this.description = description;
		this.inputSchema = inputSchema;
		this.readOnly = readOnly;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public JSONObject getInputSchema() {
		return inputSchema;
	}

	public void setInputSchema(JSONObject inputSchema) {
		this.inputSchema = inputSchema;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
}
