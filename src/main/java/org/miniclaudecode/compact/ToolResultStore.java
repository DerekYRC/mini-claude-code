package org.miniclaudecode.compact;

import org.miniclaudecode.core.ToolResultBlock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 把超大的 tool_result 写到工作目录下的文件里。
 *
 * 这样模型仍能看到输出预览和完整文件位置，同时避免旧的大输出继续挤占上下文。
 */
public class ToolResultStore {

	private static final int PREVIEW_CHARS = 2000;

	private final File outputDir;

	public ToolResultStore(File workdir) {
		this.outputDir = new File(workdir, ".task_outputs/tool-results");
	}

	public String persist(ToolResultBlock block) {
		ensureOutputDir();
		String safeId = safeId(block.getToolUseId());
		File file = new File(outputDir, safeId + ".txt");
		String content = block.getContent() == null ? "" : block.getContent();
		try {
			Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to persist tool result: " + file, e);
		}
		return "<persisted-output>\n"
				+ "Full output: " + relativePath(file) + "\n"
				+ "Preview:\n"
				+ preview(content)
				+ "\n</persisted-output>";
	}

	private void ensureOutputDir() {
		if (!outputDir.exists() && !outputDir.mkdirs()) {
			throw new IllegalStateException("Failed to create directory: " + outputDir);
		}
	}

	private String safeId(String id) {
		if (id == null || id.isBlank()) {
			return "tool_result";
		}
		return id.replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private String preview(String content) {
		if (content.length() <= PREVIEW_CHARS) {
			return content;
		}
		return content.substring(0, PREVIEW_CHARS) + "\n...[truncated preview]";
	}

	private String relativePath(File file) {
		return ".task_outputs/tool-results/" + file.getName();
	}
}
