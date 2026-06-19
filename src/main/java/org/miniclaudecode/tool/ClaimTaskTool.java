package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskService;

/**
 * 认领可开始的任务。
 */
public class ClaimTaskTool implements Tool {

	private final TaskService taskService;

	private final String defaultOwner;

	public ClaimTaskTool(TaskService taskService) {
		this(taskService, null);
	}

	public ClaimTaskTool(TaskService taskService, String defaultOwner) {
		this.taskService = taskService;
		this.defaultOwner = defaultOwner;
	}

	/*
	 * {
	 *   "name": "claim_task",
	 *   "description": "Claim a pending task. Sets owner, changes status to in_progress.",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "task_id": {"type": "string"}
	 *     },
	 *     "required": ["task_id"]
	 *   }
	 * }
	 */
	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("task_id", new JSONObject().fluentPut("type", "string"));
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
			String taskId = input == null ? null : input.getString("task_id");
			String owner = input == null ? null : input.getString("owner");
			// 队友侧工具由 harness 注入队友名，避免模型把任务认领给错误身份。
			if (defaultOwner != null && !defaultOwner.isBlank()) {
				owner = defaultOwner;
			}
			return new ToolResult(taskService.claimTask(taskId, owner));
		}
		catch (RuntimeException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
