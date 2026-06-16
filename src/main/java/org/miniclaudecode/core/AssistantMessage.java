package org.miniclaudecode.core;

import java.util.List;

/**
 * 模型返回的 assistant 消息，包含 content blocks 和 stop_reason。
 */
public class AssistantMessage {

	private List<ContentBlock> content;

	private String stopReason;

	public AssistantMessage(List<ContentBlock> content, String stopReason) {
		this.content = content;
		this.stopReason = stopReason;
	}

	public List<ContentBlock> getContent() {
		return content;
	}

	public void setContent(List<ContentBlock> content) {
		this.content = content;
	}

	public String getStopReason() {
		return stopReason;
	}

	public void setStopReason(String stopReason) {
		this.stopReason = stopReason;
	}
}
