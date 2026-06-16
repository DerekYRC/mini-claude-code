package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.skill.Skill;
import org.miniclaudecode.skill.SkillRegistry;

/**
 * 按名称加载技能正文的工具。
 *
 * 工具只接受 SkillRegistry 已扫描到的技能名，不接受任意文件路径，
 * 这样可以把“按需加载”和“文件访问边界”放在同一个最小示例里讲清楚。
 */
public class LoadSkillTool implements Tool {

	private final SkillRegistry skillRegistry;

	public LoadSkillTool(SkillRegistry skillRegistry) {
		this.skillRegistry = skillRegistry;
	}

	@Override
	/*
	 * {
	 *   "name": "load_skill",
	 *   "description": "Load specialized knowledge by name.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "name": {"type": "string", "description": "Skill name to load"}
	 *     },
	 *     "required": ["name"]
	 *   }
	 * }
	 */
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
		// 用显式标签包住正文，让模型能区分“工具返回的技能内容”和普通对话文本。
		return new ToolResult("<skill name=\"" + skill.getName() + "\">\n"
				+ skill.getBody() + "\n</skill>");
	}
}
