package org.miniclaudecode.memory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * `.memory/` 文件仓库。
 *
 * 每条记忆是一个 Markdown 文件，`MEMORY.md` 只是索引，正文按需读取。
 */
public class MemoryStore {

	private final File memoryDir;

	public MemoryStore(File workdir) {
		this.memoryDir = new File(workdir, ".memory");
	}

	public File getMemoryDir() {
		return memoryDir;
	}

	public List<Memory> list() {
		ensureMemoryDir();
		File[] files = memoryDir.listFiles(file -> file.isFile()
				&& file.getName().endsWith(".md")
				&& !"MEMORY.md".equals(file.getName()));
		List<Memory> memories = new ArrayList<>();
		if (files == null) {
			return memories;
		}
		Arrays.sort(files);
		for (File file : files) {
			try {
				memories.add(parse(file));
			}
			catch (IOException e) {
				System.out.println("Skip memory " + file.getName() + ": " + e.getMessage());
			}
		}
		return memories;
	}

	public Memory findByFilename(String filename) {
		if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/")) {
			return null;
		}
		File file = new File(memoryDir, filename);
		if (!file.isFile()) {
			return null;
		}
		try {
			return parse(file);
		}
		catch (IOException e) {
			return null;
		}
	}

	public Memory write(Memory memory) {
		ensureMemoryDir();
		String filename = memory.getFilename();
		if (filename == null || filename.isBlank()) {
			filename = uniqueFilename(slug(memory.getName()));
			memory.setFilename(filename);
		}
		memory.setType(safeType(memory.getType()));
		File file = new File(memoryDir, filename);
		try {
			Files.writeString(file.toPath(), render(memory), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write memory: " + file, e);
		}
		rebuildIndex();
		return memory;
	}

	public void deleteMemoryFiles() {
		ensureMemoryDir();
		File[] files = memoryDir.listFiles(file -> file.isFile()
				&& file.getName().endsWith(".md")
				&& !"MEMORY.md".equals(file.getName()));
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (!file.delete()) {
				throw new IllegalStateException("Failed to delete memory: " + file);
			}
		}
		rebuildIndex();
	}

	public String indexContent() {
		File index = new File(memoryDir, "MEMORY.md");
		if (!index.isFile()) {
			return "(no memories yet)";
		}
		try {
			String content = Files.readString(index.toPath(), StandardCharsets.UTF_8).trim();
			return content.isBlank() ? "(no memories yet)" : content;
		}
		catch (IOException e) {
			return "(failed to read memory index: " + e.getMessage() + ")";
		}
	}

	public void rebuildIndex() {
		ensureMemoryDir();
		List<Memory> memories = list();
		StringBuilder builder = new StringBuilder();
		for (Memory memory : memories) {
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append("- [")
					.append(memory.getName())
					.append("](")
					.append(memory.getFilename())
					.append(") - ")
					.append(memory.getDescription());
		}
		try {
			Files.writeString(new File(memoryDir, "MEMORY.md").toPath(), builder.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write MEMORY.md", e);
		}
	}

	private Memory parse(File file) throws IOException {
		String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
		String name = stripExtension(file.getName());
		String description = "";
		String type = "user";
		String body = raw;
		if (raw.startsWith("---")) {
			String[] parts = raw.split("---", 3);
			if (parts.length >= 3) {
				for (String line : parts[1].split("\\R")) {
					if (line.startsWith("name:")) {
						name = line.substring("name:".length()).trim();
					}
					else if (line.startsWith("description:")) {
						description = line.substring("description:".length()).trim();
					}
					else if (line.startsWith("type:")) {
						type = safeType(line.substring("type:".length()).trim());
					}
				}
				body = parts[2].trim();
			}
		}
		return new Memory(file.getName(), name, description, safeType(type), body);
	}

	private String render(Memory memory) {
		return "---\n"
				+ "name: " + nullToEmpty(memory.getName()) + "\n"
				+ "description: " + nullToEmpty(memory.getDescription()) + "\n"
				+ "type: " + safeType(memory.getType()) + "\n"
				+ "---\n\n"
				+ nullToEmpty(memory.getBody()).trim()
				+ "\n";
	}

	private void ensureMemoryDir() {
		if (!memoryDir.exists() && !memoryDir.mkdirs()) {
			throw new IllegalStateException("Failed to create directory: " + memoryDir);
		}
	}

	private String uniqueFilename(String slug) {
		String base = slug == null || slug.isBlank() ? "memory-" + System.currentTimeMillis() : slug;
		String filename = base + ".md";
		int index = 2;
		while (new File(memoryDir, filename).exists()) {
			filename = base + "-" + index + ".md";
			index++;
		}
		return filename;
	}

	private String slug(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase()
				.replaceAll("[^a-z0-9\\-]+", "-")
				.replaceAll("-+", "-")
				.replaceAll("^-|-$", "");
	}

	private String safeType(String type) {
		if ("feedback".equals(type) || "project".equals(type) || "reference".equals(type)) {
			return type;
		}
		return "user";
	}

	private String stripExtension(String filename) {
		if (filename.endsWith(".md")) {
			return filename.substring(0, filename.length() - 3);
		}
		return filename;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
