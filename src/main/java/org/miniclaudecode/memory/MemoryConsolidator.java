package org.miniclaudecode.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.llm.LlmClient;

import java.util.Collections;
import java.util.List;

/**
 * 低频记忆整理。
 *
 * 教学版用数量阈值代替真实系统里的时间、会话、锁等多层门控。
 */
public class MemoryConsolidator {

	private static final int CONSOLIDATE_THRESHOLD = 10;

	private final LlmClient llmClient;

	public MemoryConsolidator(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	public void consolidate(MemoryStore store) {
		List<Memory> memories = store.list();
		if (memories.size() < CONSOLIDATE_THRESHOLD) {
			return;
		}
		String prompt = "Merge and deduplicate these memories.\n"
				+ "Return ONLY a JSON array. Each item must contain name, type, description, body.\n"
				+ "type must be one of user, feedback, project, reference.\n\n"
				+ JSON.toJSONString(memories);
		AssistantMessage response = llmClient.chat(Collections.singletonList(Message.user(prompt)),
				Collections.emptyList());
		JSONArray array = firstJsonArray(extractText(response));
		if (array == null || array.isEmpty()) {
			return;
		}
		store.deleteMemoryFiles();
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
			store.write(new Memory(null,
					item.getString("name"),
					description,
					item.getString("type"),
					body));
		}
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
}
