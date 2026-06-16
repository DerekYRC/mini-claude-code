package org.miniclaudecode.compact;

import com.alibaba.fastjson.JSON;
import org.miniclaudecode.core.Message;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 压缩前保存完整 transcript。
 *
 * s08 不做 transcript 检索，只把恢复材料落盘，避免为了压缩章节引入额外搜索工具。
 */
public class TranscriptStore {

	private final File transcriptDir;

	public TranscriptStore(File workdir) {
		this.transcriptDir = new File(workdir, ".transcripts");
	}

	public File write(List<Message> messages) {
		ensureTranscriptDir();
		File file = new File(transcriptDir, "transcript_" + System.currentTimeMillis() + ".jsonl");
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			for (Message message : messages) {
				writer.write(JSON.toJSONString(message));
				writer.newLine();
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write transcript: " + file, e);
		}
		System.out.println("[transcript saved: " + file.getPath() + "]");
		return file;
	}

	private void ensureTranscriptDir() {
		if (!transcriptDir.exists() && !transcriptDir.mkdirs()) {
			throw new IllegalStateException("Failed to create directory: " + transcriptDir);
		}
	}
}
