package org.miniclaudecode.memory;

/**
 * 一条文件系统记忆。
 *
 * s09 使用普通 Java 类表达纯数据，便于直接对应 .memory/*.md 的 frontmatter。
 */
public class Memory {

	private String filename;

	private String name;

	private String description;

	private String type;

	private String body;

	public Memory() {
	}

	public Memory(String filename, String name, String description, String type, String body) {
		this.filename = filename;
		this.name = name;
		this.description = description;
		this.type = type;
		this.body = body;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
