package org.miniclaudecode.skill;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SkillRegistry {

	private final Map<String, Skill> skills = new LinkedHashMap<>();

	public SkillRegistry(File skillsDir) {
		scan(skillsDir);
	}

	public Skill find(String name) {
		return skills.get(name);
	}

	public Collection<Skill> all() {
		return skills.values();
	}

	public String getDescriptions() {
		if (skills.isEmpty()) {
			return "(no skills available)";
		}
		StringBuilder builder = new StringBuilder();
		for (Skill skill : skills.values()) {
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append("  - ")
					.append(skill.getName())
					.append(": ")
					.append(skill.getDescription());
		}
		return builder.toString();
	}

	private void scan(File skillsDir) {
		File[] dirs = skillsDir.listFiles(File::isDirectory);
		if (dirs == null) {
			return;
		}
		Arrays.sort(dirs);
		for (File dir : dirs) {
			File skillFile = new File(dir, "SKILL.md");
			if (!skillFile.isFile()) {
				continue;
			}
			try {
				String raw = Files.readString(skillFile.toPath(), StandardCharsets.UTF_8);
				Skill skill = parse(dir.getName(), raw);
				skills.put(skill.getName(), skill);
			}
			catch (IOException e) {
				System.out.println("Skip skill " + dir.getName() + ": " + e.getMessage());
			}
		}
	}

	private Skill parse(String fallbackName, String raw) {
		String name = fallbackName;
		String body = raw;
		String description = firstHeading(raw);
		if (raw.startsWith("---")) {
			String[] parts = raw.split("---", 3);
			if (parts.length >= 3) {
				String[] lines = parts[1].split("\\R");
				for (String line : lines) {
					if (line.startsWith("name:")) {
						name = line.substring("name:".length()).trim();
					}
					else if (line.startsWith("description:")) {
						description = line.substring("description:".length()).trim();
					}
				}
				body = parts[2].trim();
				if (description == null || description.isBlank()) {
					description = firstHeading(body);
				}
			}
		}
		return new Skill(name, description, body);
	}

	private String firstHeading(String content) {
		for (String line : content.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("#")) {
				return trimmed.replaceFirst("^#+", "").trim();
			}
		}
		return "";
	}
}
