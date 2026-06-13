package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.skill.Skill;
import org.miniclaudecode.skill.SkillRegistry;

public class LoadSkillTool implements Tool {

	private final SkillRegistry skillRegistry;

	public LoadSkillTool(SkillRegistry skillRegistry) {
		this.skillRegistry = skillRegistry;
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("name", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Skill name to load"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("name"));
		return new ToolDefinition("load_skill", "Load specialized knowledge by name.", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String name = input == null ? "" : input.getString("name");
		if (name == null || name.isBlank()) {
			return new ToolResult("Error: No skill name provided");
		}
		Skill skill = skillRegistry.find(name);
		if (skill == null) {
			return new ToolResult("Skill not found: " + name);
		}
		return new ToolResult("<skill name=\"" + skill.getName() + "\">\n"
				+ skill.getBody() + "\n</skill>");
	}
}
