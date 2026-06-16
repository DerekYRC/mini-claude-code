package org.miniclaudecode.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.compact.MessageInspector;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.llm.LlmClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从最近对话提取新记忆。
 *
 * 真实系统会有更复杂的门控；教学版在每轮结束后跑一次，便于观察文件变化。
 */
public class MemoryExtractor {

	private final MessageInspector inspector = new MessageInspector();

	private final LlmClient llmClient;

	public MemoryExtractor(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	public List<Memory> extract(List<Message> messages, List<Memory> existing) {
		String dialogue = recentDialogue(messages);
		String prompt = "Extract user preferences, constraints, feedback, project facts, or references from this dialogue.\n"
				+ "Return ONLY a JSON array. Each item must contain:\n"
				+ "name, type, description, body.\n"
				+ "type must be one of user, feedback, project, reference.\n"
				+ "If nothing new or already covered by existing memories, return [].\n\n"
				+ "Existing memories:\n" + existingCatalog(existing) + "\n\n"
				+ "Dialogue:\n" + dialogue;
		try {
			AssistantMessage response = llmClient.chat(Collections.singletonList(Message.user(prompt)),
					Collections.emptyList());
			List<Memory> extracted = parseMemories(firstJsonArray(extractText(response)));
			if (!extracted.isEmpty()) {
				return extracted;
			}
		}
		catch (RuntimeException e) {
			// 解析失败时继续走显式“请记住”的保底路径，保证教学 demo 可观察。
		}
		return explicitRememberFallback(currentUserText(messages));
	}

	private List<Memory> parseMemories(JSONArray array) {
		List<Memory> memories = new ArrayList<>();
		if (array == null) {
			return memories;
		}
		for (int i = 0; i < array.size(); i++) {
			JSONObject item = array.getJSONObject(i);
			if (item == null) {
				continue;
			}
			String description = item.getString("description");
			String body = item.getString("body");
			if (description == null || description.isBlank() || body == null || body.isBlank()) {
				continue;
			}
			memories.add(new Memory(null,
					blankToDefault(item.getString("name"), "memory-" + System.currentTimeMillis() + "-" + i),
					description,
					safeType(item.getString("type")),
					body));
		}
		return memories;
	}

	private List<Memory> explicitRememberFallback(String dialogue) {
		int index = dialogue.indexOf("请记住");
		if (index < 0) {
			return Collections.emptyList();
		}
		String remembered = dialogue.substring(index)
				.replaceFirst("^请记住[:：]?", "")
				.split("\\R", 2)[0]
				.trim();
		if (remembered.isBlank()) {
			return Collections.emptyList();
		}
		List<Memory> memories = new ArrayList<>();
		memories.add(new Memory(null,
				"user-remember-" + System.currentTimeMillis(),
				remembered,
				"user",
				remembered));
		return memories;
	}

	private String currentUserText(List<Message> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			Message message = messages.get(i);
			if (!"user".equals(message.getRole())) {
				continue;
			}
			String text = inspector.textOf(message);
			int open = text.lastIndexOf("<user_message>");
			int close = text.lastIndexOf("</user_message>");
			if (open >= 0 && close > open) {
				return text.substring(open + "<user_message>".length(), close).trim();
			}
			return text;
		}
		return "";
	}

	private String recentDialogue(List<Message> messages) {
		int start = Math.max(0, messages.size() - 10);
		StringBuilder builder = new StringBuilder();
		for (int i = start; i < messages.size(); i++) {
			Message message = messages.get(i);
			builder.append(message.getRole())
					.append(": ")
					.append(inspector.textOf(message))
					.append("\n");
		}
		return builder.toString();
	}

	private String existingCatalog(List<Memory> existing) {
		if (existing.isEmpty()) {
			return "(none)";
		}
		StringBuilder builder = new StringBuilder();
		for (Memory memory : existing) {
			builder.append("- ")
					.append(memory.getName())
					.append(": ")
					.append(memory.getDescription())
					.append("\n");
		}
		return builder.toString();
	}

	private JSONArray firstJsonArray(String text) {
		if (text == null) {
			return null;
		}
		int start = text.indexOf('[');
		int end = text.lastIndexOf(']');
		if (start < 0 || end < start) {
			return null;
		}
		return JSON.parseArray(text.substring(start, end + 1));
	}

	private String extractText(AssistantMessage response) {
		StringBuilder builder = new StringBuilder();
		for (ContentBlock block : response.getContent()) {
			if (block instanceof TextBlock) {
				builder.append(((TextBlock) block).getText());
			}
		}
		return builder.toString();
	}

	private String safeType(String type) {
		if ("feedback".equals(type) || "project".equals(type) || "reference".equals(type)) {
			return type;
		}
		return "user";
	}

	private String blankToDefault(String value, String defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}
}
