package org.miniclaudecode.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.llm.LlmClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 根据当前问题选择相关记忆。
 *
 * 主路径使用一次 LLM side-query，失败时退回关键词匹配，保证 demo 不因 JSON 波动中断。
 */
public class MemorySelector {

	private static final int MAX_SELECTED = 5;

	private final LlmClient llmClient;

	public MemorySelector(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	public List<Memory> select(List<Memory> memories, String recentConversation) {
		if (memories.isEmpty()) {
			return Collections.emptyList();
		}
		List<Memory> selected = selectWithLlm(memories, recentConversation);
		if (!selected.isEmpty()) {
			return selected;
		}
		return selectWithKeywords(memories, recentConversation);
	}

	private List<Memory> selectWithLlm(List<Memory> memories, String recentConversation) {
		String prompt = "Given the recent conversation and the memory catalog below,\n"
				+ "select the indices of memories that are clearly relevant.\n"
				+ "Return ONLY a JSON array of integers, e.g. [0, 3].\n"
				+ "If none are relevant, return [].\n\n"
				+ "Recent conversation:\n" + recentConversation + "\n\n"
				+ "Memory catalog:\n" + catalog(memories);
		try {
			AssistantMessage response = llmClient.chat(Collections.singletonList(Message.user(prompt)),
					Collections.emptyList());
			JSONArray indices = firstJsonArray(extractText(response));
			if (indices == null) {
				return Collections.emptyList();
			}
			List<Memory> selected = new ArrayList<>();
			for (int i = 0; i < indices.size() && selected.size() < MAX_SELECTED; i++) {
				Integer index = indices.getInteger(i);
				if (index != null && index >= 0 && index < memories.size()) {
					selected.add(memories.get(index));
				}
			}
			return selected;
		}
		catch (RuntimeException e) {
			return Collections.emptyList();
		}
	}

	private List<Memory> selectWithKeywords(List<Memory> memories, String recentConversation) {
		List<Memory> selected = new ArrayList<>();
		String query = recentConversation == null ? "" : recentConversation.toLowerCase(Locale.ROOT);
		for (Memory memory : memories) {
			if (selected.size() >= MAX_SELECTED) {
				break;
			}
			String haystack = (memory.getName() + " " + memory.getDescription()).toLowerCase(Locale.ROOT);
			for (String token : query.split("[^a-z0-9]+")) {
				if (token.length() >= 3 && haystack.contains(token)) {
					selected.add(memory);
					break;
				}
			}
		}
		return selected;
	}

	private String catalog(List<Memory> memories) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < memories.size(); i++) {
			Memory memory = memories.get(i);
			builder.append(i)
					.append(": ")
					.append(memory.getName())
					.append(" - ")
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
}
