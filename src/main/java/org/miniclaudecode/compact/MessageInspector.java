package org.miniclaudecode.compact;

import com.alibaba.fastjson.JSON;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolResultBlock;
import org.miniclaudecode.core.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * s08 专用的消息结构检查器。
 *
 * 压缩代码需要频繁判断 tool_use/tool_result 配对，把这些判断集中到一个类里，
 * 可以避免压缩管线到处散落 instanceof 细节。
 */
public class MessageInspector {

	public int estimateSize(List<Message> messages) {
		// 教学版用 JSON 字符长度近似 token 压力，重点展示压缩位置而不是 tokenizer 精度。
		return JSON.toJSONString(messages).length();
	}

	public boolean hasToolUse(Message message) {
		if (message == null || !"assistant".equals(message.getRole())) {
			return false;
		}
		for (ContentBlock block : message.getContent()) {
			if (block instanceof ToolUseBlock) {
				return true;
			}
		}
		return false;
	}

	public boolean isToolResultMessage(Message message) {
		return message != null && "user".equals(message.getRole()) && !toolResults(message).isEmpty();
	}

	public List<ToolResultBlock> toolResults(Message message) {
		List<ToolResultBlock> results = new ArrayList<>();
		if (message == null || message.getContent() == null) {
			return results;
		}
		for (ContentBlock block : message.getContent()) {
			if (block instanceof ToolResultBlock) {
				results.add((ToolResultBlock) block);
			}
		}
		return results;
	}

	public String textOf(Message message) {
		if (message == null || message.getContent() == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (ContentBlock block : message.getContent()) {
			if (block instanceof TextBlock) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(((TextBlock) block).getText());
			}
			else if (block instanceof ToolResultBlock) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(((ToolResultBlock) block).getContent());
			}
			else if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append("tool_use:")
						.append(toolUse.getName())
						.append(" ")
						.append(toolUse.getInput());
			}
		}
		return builder.toString();
	}
}
