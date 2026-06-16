package org.miniclaudecode.skill;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能目录。
 *
 * 启动时只把 name/description 这类便宜信息放进 system prompt，
 * 真正的 SKILL.md 正文等模型调用 load_skill 时再加载。
 */
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
			// 目录里只放技能名和一句说明，避免把所有技能正文一次性塞进上下文。
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
				// 这里会读出完整文件，但只把目录信息注入 prompt；正文保存在 registry 里按需取用。
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
				// frontmatter 的 name/description 是技能目录，正文 body 才是 load_skill 返回的内容。
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
