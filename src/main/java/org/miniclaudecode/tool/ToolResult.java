package org.miniclaudecode.tool;

/**
 * Java 工具执行后的文本结果，最终会被包装成 ToolResultBlock 回传给模型。
 */
public class ToolResult {

	private String content;

	public ToolResult(String content) {
		this.content = content;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
