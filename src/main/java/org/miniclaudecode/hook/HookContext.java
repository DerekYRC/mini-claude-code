package org.miniclaudecode.hook;

import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.tool.ToolResult;

import java.util.List;

public class HookContext {

	private String event;

	private String userPrompt;

	private List<Message> messages;

	private ToolUseBlock toolUse;

	private ToolResult toolResult;

	public HookContext(String event) {
		this.event = event;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getUserPrompt() {
		return userPrompt;
	}

	public void setUserPrompt(String userPrompt) {
		this.userPrompt = userPrompt;
	}

	public List<Message> getMessages() {
		return messages;
	}

	public void setMessages(List<Message> messages) {
		this.messages = messages;
	}

	public ToolUseBlock getToolUse() {
		return toolUse;
	}

	public void setToolUse(ToolUseBlock toolUse) {
		this.toolUse = toolUse;
	}

	public ToolResult getToolResult() {
		return toolResult;
	}

	public void setToolResult(ToolResult toolResult) {
		this.toolResult = toolResult;
	}
}
