package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskService;

/**
 * 读取单个任务的完整 JSON。
 */
public class GetTaskTool implements Tool {

	private final TaskService taskService;

	public GetTaskTool(TaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	/*
	 * {
	 *   "name": "get_task",
	 *   "description": "Get full details of a specific task by ID.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "task_id": {"type": "string"}
	 *     },
	 *     "required": ["task_id"]
	 *   }
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("task_id", new JSONObject().fluentPut("type", "string"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("task_id"));
		return new ToolDefinition("get_task",
				"Get full details of a specific task by ID.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		try {
			return new ToolResult(taskService.getTask(input == null ? null : input.getString("task_id")));
		}
		catch (RuntimeException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
