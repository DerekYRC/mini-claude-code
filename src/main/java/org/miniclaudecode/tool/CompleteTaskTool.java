package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskService;

/**
 * 完成任务，并报告刚解锁的下游任务。
 */
public class CompleteTaskTool implements Tool {

	private final TaskService taskService;

	public CompleteTaskTool(TaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	/*
	 * {
	 *   "name": "complete_task",
	 *   "description": "Complete an in-progress task. Reports unblocked downstream tasks.",
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
		return new ToolDefinition("complete_task",
				"Complete an in-progress task. Reports unblocked downstream tasks.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		try {
			return new ToolResult(taskService.completeTask(input == null ? null : input.getString("task_id")));
		}
		catch (RuntimeException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
