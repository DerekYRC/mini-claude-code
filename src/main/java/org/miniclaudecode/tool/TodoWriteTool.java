package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 计划记录工具。
 *
 * s05 把“先列计划”做成一个普通工具，而不是写进 AgentLoop。
 * 工具只保存当前 todo 列表，不直接执行任何任务。
 */
public class TodoWriteTool implements Tool {

	private final List<TodoItem> currentTodos = new ArrayList<>();

	@Override
	/*
	 * {
	 *   "name": "todo_write",
	 *   "description": "Create or replace the current task list",
	 *   "input_schema": {
	 *     "type": "object",
	 *     "properties": {
	 *       "todos": {
	 *         "type": "array",
	 *         "items": {
	 *           "type": "object",
	 *           "properties": {
	 *             "content": {"type": "string", "description": "Task content"},
	 *             "status": {
	 *               "type": "string",
	 *               "enum": ["pending", "in_progress", "completed"],
	 *               "description": "Task status"
	 *             }
	 *           },
	 *           "required": ["content", "status"]
	 *         },
	 *         "description": "Full current todo list"
	 *       }
	 *     },
	 *     "required": ["todos"]
	 *   }
	 * }
	 */
	public ToolDefinition getDefinition() {
		JSONObject todoProperties = new JSONObject()
				.fluentPut("content", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Task content"))
				.fluentPut("status", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("enum", new JSONArray()
								.fluentAdd("pending")
								.fluentAdd("in_progress")
								.fluentAdd("completed"))
						.fluentPut("description", "Task status"));
		JSONObject todoSchema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", todoProperties)
				.fluentPut("required", new JSONArray().fluentAdd("content").fluentAdd("status"));
		JSONObject properties = new JSONObject()
				.fluentPut("todos", new JSONObject()
						.fluentPut("type", "array")
						.fluentPut("items", todoSchema)
						.fluentPut("description", "Full current todo list"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("todos"));
		return new ToolDefinition("todo_write", "Create or replace the current task list", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		JSONArray todos = todosArray(input);
		if (todos == null) {
			return new ToolResult("Error: todos must be an array");
		}

		List<TodoItem> nextTodos = new ArrayList<>();
		for (int i = 0; i < todos.size(); i++) {
			// 模型每次传入完整列表，工具用它替换当前内存状态。
			JSONObject item = todos.getJSONObject(i);
			if (item == null) {
				return new ToolResult("Error: todos[" + i + "] must be an object");
			}
			String content = item.getString("content");
			String status = item.getString("status");
			if (content == null || content.isBlank()) {
				return new ToolResult("Error: todos[" + i + "] missing content");
			}
			if (!Arrays.asList("pending", "in_progress", "completed").contains(status)) {
				// 教学版只允许三种状态，避免 todo 状态变成开放文本。
				return new ToolResult("Error: todos[" + i + "] has invalid status: " + status);
			}
			nextTodos.add(new TodoItem(content, status));
		}

		currentTodos.clear();
		currentTodos.addAll(nextTodos);
		return new ToolResult("Updated " + currentTodos.size() + " tasks\n" + render());
	}

	private JSONArray todosArray(JSONObject input) {
		if (input == null || !input.containsKey("todos")) {
			return null;
		}
		Object raw = input.get("todos");
		if (raw instanceof JSONArray) {
			return (JSONArray) raw;
		}
		if (raw instanceof String) {
			try {
				return JSON.parseArray((String) raw);
			}
			catch (RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	private String render() {
		if (currentTodos.isEmpty()) {
			return "(no todos)";
		}
		StringBuilder builder = new StringBuilder("Current tasks:");
		for (TodoItem todo : currentTodos) {
			builder.append("\n- [")
					.append(todo.getStatus())
					.append("] ")
					.append(todo.getContent());
		}
		return builder.toString();
	}
}
