package org.miniclaudecode.tool;

/**
 * 普通数据类，不使用 record，保存任务内容和状态。
 */
public class TodoItem {

	private String content;

	private String status;

	public TodoItem(String content, String status) {
		this.content = content;
		this.status = status;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
