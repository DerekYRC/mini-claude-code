package org.miniclaudecode.llm;

import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.tool.ToolDefinition;

import java.util.List;

public interface LlmClient {

	AssistantMessage chat(List<Message> messages, List<ToolDefinition> tools);
}
