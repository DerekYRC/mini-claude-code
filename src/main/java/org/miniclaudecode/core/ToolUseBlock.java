package org.miniclaudecode.core;

import com.alibaba.fastjson.JSONObject;

/**
 * 模型请求调用工具时返回的 block，name 决定要执行哪个 Tool。
 */
public class ToolUseBlock extends ContentBlock {

	private String id;

	private String name;

	private JSONObject input;

	public ToolUseBlock() {
		super("tool_use");
	}

	public ToolUseBlock(String id, String name, JSONObject input) {
		super("tool_use");
		this.id = id;
		this.name = name;
		this.input = input;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public JSONObject getInput() {
		return input;
	}

	public void setInput(JSONObject input) {
		this.input = input;
	}
}
