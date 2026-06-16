package org.miniclaudecode.compact;

import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolResultBlock;
import org.miniclaudecode.llm.LlmClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * s08 的四层压缩管线。
 *
 * 压缩按“便宜且保真优先”的顺序执行，最后才调用 LLM 做摘要。
 */
public class CompactionPipeline {

	private static final int MAX_MESSAGES = 50;

	private static final int KEEP_RECENT_TOOL_RESULTS = 3;

	private static final int TOOL_RESULT_BUDGET = 200000;

	private static final int AUTO_COMPACT_THRESHOLD = 50000;

	private static final int MICRO_COMPACT_MIN_LENGTH = 120;

	private final MessageInspector inspector = new MessageInspector();

	private final ToolResultStore toolResultStore;

	private final TranscriptStore transcriptStore;

	private final LlmClient summaryClient;

	public CompactionPipeline(File workdir, LlmClient summaryClient) {
		this.toolResultStore = new ToolResultStore(workdir);
		this.transcriptStore = new TranscriptStore(workdir);
		this.summaryClient = summaryClient;
	}

	public void beforeLlm(List<Message> messages) {
		toolResultBudget(messages);
		snipCompact(messages);
		microCompact(messages);
		if (inspector.estimateSize(messages) > AUTO_COMPACT_THRESHOLD) {
			replaceAll(messages, compactHistory(messages, "auto compact"));
		}
	}

	public List<Message> compactHistory(List<Message> messages, String focus) {
		transcriptStore.write(messages);
		String prompt = "Summarize this coding-agent conversation so work can continue.\n"
				+ "Preserve:\n"
				+ "1. current goal\n"
				+ "2. key findings and decisions\n"
				+ "3. files read or changed\n"
				+ "4. remaining work\n"
				+ "5. user constraints\n"
				+ "Focus: " + blankToDefault(focus, "compact history") + "\n\n"
				+ renderMessages(messages);
		AssistantMessage summary = summaryClient.chat(Collections.singletonList(Message.user(prompt)),
				Collections.emptyList());
		return Collections.singletonList(Message.user("[Compacted]\n\n" + extractText(summary)));
	}

	public List<Message> reactiveCompact(List<Message> messages) {
		List<Message> compacted = new ArrayList<>(compactHistory(messages, "reactive prompt too long"));
		int start = Math.max(0, messages.size() - 10);
		start = moveStartAwayFromOrphanToolResult(messages, start);
		for (int i = start; i < messages.size(); i++) {
			compacted.add(messages.get(i));
		}
		return compacted;
	}

	private void toolResultBudget(List<Message> messages) {
		if (messages.isEmpty()) {
			return;
		}
		Message last = messages.get(messages.size() - 1);
		if (!inspector.isToolResultMessage(last)) {
			return;
		}
		List<ToolResultBlock> results = inspector.toolResults(last);
		int total = totalContentLength(results);
		if (total <= TOOL_RESULT_BUDGET) {
			return;
		}
		List<ToolResultBlock> bySize = new ArrayList<>(results);
		bySize.sort(Comparator.comparingInt(this::contentLength).reversed());
		for (ToolResultBlock result : bySize) {
			if (total <= TOOL_RESULT_BUDGET) {
				break;
			}
			String original = result.getContent() == null ? "" : result.getContent();
			String persisted = toolResultStore.persist(result);
			result.setContent(persisted);
			total -= original.length();
			total += persisted.length();
		}
	}

	private void snipCompact(List<Message> messages) {
		if (messages.size() <= MAX_MESSAGES) {
			return;
		}
		int head = 3;
		int start = messages.size() - (MAX_MESSAGES - head);
		while (head < start && inspector.hasToolUse(messages.get(head - 1))) {
			// assistant(tool_use) 后面必须紧跟 user(tool_result)，所以切口向后扩。
			head++;
		}
		start = moveStartAwayFromOrphanToolResult(messages, start);
		if (head >= start) {
			return;
		}
		int removed = start - head;
		List<Message> next = new ArrayList<>();
		next.addAll(messages.subList(0, head));
		next.add(Message.user("[snipped " + removed + " messages]"));
		next.addAll(messages.subList(start, messages.size()));
		replaceAll(messages, next);
	}

	private void microCompact(List<Message> messages) {
		List<ToolResultBlock> results = new ArrayList<>();
		for (Message message : messages) {
			results.addAll(inspector.toolResults(message));
		}
		int fullFrom = Math.max(0, results.size() - KEEP_RECENT_TOOL_RESULTS);
		for (int i = 0; i < fullFrom; i++) {
			ToolResultBlock result = results.get(i);
			if (contentLength(result) > MICRO_COMPACT_MIN_LENGTH) {
				result.setContent("[Earlier tool result compacted. Re-run if needed.]");
			}
		}
	}

	private int moveStartAwayFromOrphanToolResult(List<Message> messages, int start) {
		int adjusted = Math.max(0, start);
		while (adjusted < messages.size() && inspector.isToolResultMessage(messages.get(adjusted))) {
			adjusted++;
		}
		return adjusted;
	}

	private int totalContentLength(List<ToolResultBlock> results) {
		int total = 0;
		for (ToolResultBlock result : results) {
			total += contentLength(result);
		}
		return total;
	}

	private int contentLength(ToolResultBlock result) {
		return result.getContent() == null ? 0 : result.getContent().length();
	}

	private String renderMessages(List<Message> messages) {
		StringBuilder builder = new StringBuilder();
		for (Message message : messages) {
			builder.append(message.getRole()).append(": ");
			String text = inspector.textOf(message);
			if (text.length() > 4000) {
				text = text.substring(0, 4000) + "\n...[message truncated for summary prompt]";
			}
			builder.append(text).append("\n\n");
		}
		return builder.toString();
	}

	private String extractText(AssistantMessage message) {
		StringBuilder builder = new StringBuilder();
		for (ContentBlock block : message.getContent()) {
			if (block instanceof TextBlock) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(((TextBlock) block).getText());
			}
		}
		if (builder.length() == 0) {
			return "Summary unavailable.";
		}
		return builder.toString();
	}

	private String blankToDefault(String value, String defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private void replaceAll(List<Message> messages, List<Message> next) {
		messages.clear();
		messages.addAll(next);
	}
}
