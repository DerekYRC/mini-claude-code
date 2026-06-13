package org.miniclaudecode.core;

import org.miniclaudecode.tool.ToolResult;

public interface AgentLoopListener {

	default void onAssistantMessage(AssistantMessage message) {
	}

	default void beforeToolUse(ToolUseBlock toolUse) {
	}

	default void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
	}

	default void onStop(AssistantMessage message) {
	}
}
