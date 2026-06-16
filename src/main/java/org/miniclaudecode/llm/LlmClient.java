package org.miniclaudecode.llm;

import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.tool.ToolDefinition;

import java.util.List;

/**
 * AgentLoop 依赖的模型接口，隐藏真实 HTTP 调用细节。
 */
public interface LlmClient {

	AssistantMessage chat(List<Message> messages, List<ToolDefinition> tools);
}
