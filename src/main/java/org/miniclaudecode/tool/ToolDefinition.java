package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;

/**
 * 暴露给模型的工具定义，对应 Anthropic Messages 的 tools 数组。
 */
public class ToolDefinition {

	private String name;

	private String description;

	private JSONObject inputSchema;

	public ToolDefinition(String name, String description, JSONObject inputSchema) {
		this.name = name;
		this.description = description;
		this.inputSchema = inputSchema;
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
}
