package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskService;

/**
 * 认领可开始的任务。
 */
public class ClaimTaskTool implements Tool {

	private final TaskService taskService;

	public ClaimTaskTool(TaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	/*
	 * {
	 *   "name": "claim_task",
	 *   "description": "Claim a pending task. Sets owner, changes status to in_progress.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "task_id": {"type": "string"},
	 *       "owner": {"type": "string"}
	 *     },
	 *     "required": ["task_id"]
	 *   }
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("task_id", new JSONObject().fluentPut("type", "string"))
				.fluentPut("owner", new JSONObject().fluentPut("type", "string"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("task_id"));
		return new ToolDefinition("claim_task",
				"Claim a pending task. Sets owner, changes status to in_progress.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		try {
			String owner = input == null ? null : input.getString("owner");
			return new ToolResult(taskService.claimTask(input == null ? null : input.getString("task_id"), owner));
		}
		catch (RuntimeException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
