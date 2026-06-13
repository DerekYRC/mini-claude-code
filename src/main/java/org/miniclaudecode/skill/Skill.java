package org.miniclaudecode.skill;

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
