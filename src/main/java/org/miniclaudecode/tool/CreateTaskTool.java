package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskRecord;
import org.miniclaudecode.task.TaskService;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建持久化任务。
 */
public class CreateTaskTool implements Tool {

	private final TaskService taskService;

	public CreateTaskTool(TaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	/*
	 * {
	 *   "name": "create_task",
	 *   "description": "Create a new task with optional blockedBy dependencies.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "subject": {"type": "string"},
	 *       "description": {"type": "string"},
	 *       "blockedBy": {"type": "array", "items": {"type": "string"}}
	 *     },
	 *     "required": ["subject"]
	 *   }
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("subject", new JSONObject().fluentPut("type", "string"))
				.fluentPut("description", new JSONObject().fluentPut("type", "string"))
				.fluentPut("blockedBy", new JSONObject()
						.fluentPut("type", "array")
						.fluentPut("items", new JSONObject().fluentPut("type", "string")));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("subject"));
		return new ToolDefinition("create_task",
				"Create a new task with optional blockedBy dependencies.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		try {
			String subject = input == null ? "" : input.getString("subject");
			String description = input == null ? "" : input.getString("description");
			JSONArray blockedByJson = input == null ? null : input.getJSONArray("blockedBy");
			TaskRecord task = taskService.createTask(subject, description, toStringList(blockedByJson));
			String deps = task.getBlockedBy().isEmpty() ? "" : " (blockedBy: " + String.join(", ", task.getBlockedBy()) + ")";
			return new ToolResult("Created " + task.getId() + ": " + task.getSubject() + deps);
		}
		catch (RuntimeException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}

	private List<String> toStringList(JSONArray array) {
		List<String> values = new ArrayList<>();
		if (array == null) {
			return values;
		}
		for (int i = 0; i < array.size(); i++) {
			String value = array.getString(i);
			if (value != null && !value.isBlank()) {
				values.add(value.trim());
			}
		}
		return values;
	}
}
