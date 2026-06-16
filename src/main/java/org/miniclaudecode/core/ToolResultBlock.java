package org.miniclaudecode.core;

/**
 * 工具执行结果 block，toolUseId 对应模型上一轮返回的 tool_use id。
 */
public class ToolResultBlock extends ContentBlock {

	private String toolUseId;

	private String content;

	public ToolResultBlock() {
		super("tool_result");
	}

	public ToolResultBlock(String toolUseId, String content) {
		super("tool_result");
		this.toolUseId = toolUseId;
		this.content = content;
	}

	public String getToolUseId() {
		return toolUseId;
	}

	public void setToolUseId(String toolUseId) {
		this.toolUseId = toolUseId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
