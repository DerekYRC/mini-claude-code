package org.miniclaudecode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Message {

	private String role;

	private List<ContentBlock> content;

	public Message(String role, List<ContentBlock> content) {
		this.role = role;
		this.content = content;
	}

	public static Message user(String text) {
		return new Message("user", Collections.singletonList(new TextBlock(text)));
	}

	public static Message user(List<ContentBlock> content) {
		return new Message("user", content);
	}

	public static Message assistant(List<ContentBlock> content) {
		return new Message("assistant", content);
	}

	public static Message toolResults(List<ToolResultBlock> results) {
		return new Message("user", new ArrayList<ContentBlock>(results));
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public List<ContentBlock> getContent() {
		return content;
	}

	public void setContent(List<ContentBlock> content) {
		this.content = content;
	}
}
