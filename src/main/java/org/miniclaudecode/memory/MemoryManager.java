package org.miniclaudecode.memory;

import org.miniclaudecode.compact.MessageInspector;
import org.miniclaudecode.core.Message;

import java.util.List;

/**
 * s09 的记忆入口。
 *
 * demo 只和这个组合类交互，避免启动入口直接知道筛选、提取、整理的细节。
 */
public class MemoryManager {

	private final MessageInspector inspector = new MessageInspector();

	private final MemoryStore store;

	private final MemorySelector selector;

	private final MemoryExtractor extractor;

	private final MemoryConsolidator consolidator;

	public MemoryManager(MemoryStore store, MemorySelector selector, MemoryExtractor extractor,
			MemoryConsolidator consolidator) {
		this.store = store;
		this.selector = selector;
		this.extractor = extractor;
		this.consolidator = consolidator;
	}

	public String systemMemoryIndex() {
		return store.indexContent();
	}

	public Message injectRelevantMemories(List<Message> history, String userText) {
		List<Memory> selected = selector.select(store.list(), recentConversation(history, userText));
		if (selected.isEmpty()) {
			return Message.user(userText);
		}
		StringBuilder builder = new StringBuilder("<relevant_memories>\n");
		for (Memory memory : selected) {
			builder.append("<memory name=\"")
					.append(memory.getName())
					.append("\" type=\"")
					.append(memory.getType())
					.append("\">\n")
					.append(memory.getBody())
					.append("\n</memory>\n");
		}
		builder.append("</relevant_memories>\n\n")
				.append("<user_message>\n")
				.append(userText)
				.append("\n</user_message>");
		return Message.user(builder.toString());
	}

	public void afterTurn(List<Message> preCompactMessages) {
		List<Memory> existing = store.list();
		List<Memory> extracted = extractor.extract(preCompactMessages, existing);
		int count = 0;
		for (Memory memory : extracted) {
			if (isDuplicate(memory, existing)) {
				continue;
			}
			store.write(memory);
			existing.add(memory);
			count++;
		}
		if (count > 0) {
			System.out.println("[Memory: extracted " + count + " new memories]");
		}
		consolidator.consolidate(store);
	}

	private boolean isDuplicate(Memory candidate, List<Memory> existing) {
		String candidateDescription = normalize(candidate.getDescription());
		String candidateBody = normalize(candidate.getBody());
		for (Memory memory : existing) {
			if (!candidateDescription.isBlank() && candidateDescription.equals(normalize(memory.getDescription()))) {
				return true;
			}
			if (!candidateBody.isBlank() && candidateBody.equals(normalize(memory.getBody()))) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
	}

	private String recentConversation(List<Message> history, String userText) {
		StringBuilder builder = new StringBuilder();
		int start = Math.max(0, history.size() - 6);
		for (int i = start; i < history.size(); i++) {
			Message message = history.get(i);
			builder.append(message.getRole())
					.append(": ")
					.append(inspector.textOf(message))
					.append("\n");
		}
		builder.append("user: ").append(userText);
		return builder.toString();
	}
}
