package org.miniclaudecode.core;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.memory.MemoryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * s09 专用 Agent 循环外壳。
 *
 * 压缩仍交给 s08 的循环处理；这里只标出“本轮结束后提取记忆”的位置。
 */
public class MemoryAgentLoop {

	private final CompactingAgentLoop compactingLoop;

	private final MemoryManager memoryManager;

	public MemoryAgentLoop(CompactingAgentLoop compactingLoop, MemoryManager memoryManager) {
		this.compactingLoop = compactingLoop;
		this.memoryManager = memoryManager;
	}

	public AssistantMessage run(List<Message> messages, List<Message> preCompactSnapshot) {
		AssistantMessage answer = compactingLoop.run(messages);
		// 提取记忆要使用压缩前的上下文，避免摘要把用户偏好、反馈等细节抹掉。
		memoryManager.afterTurn(preCompactSnapshot);
		return answer;
	}

	public List<Message> snapshot(List<Message> messages) {
		List<Message> copied = new ArrayList<>();
		for (Message message : messages) {
			List<ContentBlock> blocks = new ArrayList<>();
			for (ContentBlock block : message.getContent()) {
				blocks.add(copyBlock(block));
			}
			copied.add(new Message(message.getRole(), blocks));
		}
		return copied;
	}

	private ContentBlock copyBlock(ContentBlock block) {
		if (block instanceof TextBlock) {
			return new TextBlock(((TextBlock) block).getText());
		}
		if (block instanceof ThinkingBlock) {
			ThinkingBlock thinking = (ThinkingBlock) block;
			return new ThinkingBlock(thinking.getThinking(), thinking.getSignature());
		}
		if (block instanceof ToolUseBlock) {
			ToolUseBlock toolUse = (ToolUseBlock) block;
			JSONObject input = toolUse.getInput() == null ? new JSONObject() : new JSONObject(toolUse.getInput());
			return new ToolUseBlock(toolUse.getId(), toolUse.getName(), input);
		}
		if (block instanceof ToolResultBlock) {
			ToolResultBlock result = (ToolResultBlock) block;
			return new ToolResultBlock(result.getToolUseId(), result.getContent());
		}
		if (block instanceof UnknownBlock) {
			UnknownBlock unknown = (UnknownBlock) block;
			JSONObject raw = unknown.getRaw() == null ? new JSONObject() : new JSONObject(unknown.getRaw());
			return new UnknownBlock(unknown.getType(), raw);
		}
		return block;
	}
}
