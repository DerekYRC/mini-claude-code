package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.task.TaskService;

/**
 * 列出任务图里的所有任务。
 */
public class ListTasksTool implements Tool {

	private final TaskService taskService;

	public ListTasksTool(TaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	/*
	 * {
	 *   "name": "list_tasks",
	 *   "description": "List all tasks with status, owner, and dependencies.",
	 *   "input_schema": {"type": "object", "properties": {}}
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", new JSONObject());
		return new ToolDefinition("list_tasks",
				"List all tasks with status, owner, and dependencies.",
				schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		return new ToolResult(taskService.listTasks());
	}
}
