package org.miniclaudecode.skill;

/**
 * 技能的纯数据对象。
 *
 * s07 为了便于新手阅读，继续使用普通 Java 类表达数据，
 * 不使用 record 或复杂元数据模型。
 */
public class Skill {

	private String name;

	private String description;

	private String body;

	public Skill(String name, String description, String body) {
		this.name = name;
		this.description = description;
		this.body = body;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getBody() {
		return body;
	}
}
