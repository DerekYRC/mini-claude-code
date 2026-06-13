package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EditFileTool implements Tool {

	private final PathGuard pathGuard;

	public EditFileTool(File workdir) {
		this.pathGuard = new PathGuard(workdir);
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("path", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "File path relative to the workdir"))
				.fluentPut("old_text", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Exact text to replace once"))
				.fluentPut("new_text", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Replacement text"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("path").fluentAdd("old_text").fluentAdd("new_text"));
		return new ToolDefinition("edit_file", "Replace exact text in a file once", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String path = input == null ? "" : input.getString("path");
		String oldText = input == null ? null : input.getString("old_text");
		String newText = input == null ? null : input.getString("new_text");
		if (path == null || path.isBlank()) {
			return new ToolResult("Error: No path provided");
		}
		if (oldText == null || newText == null) {
			return new ToolResult("Error: old_text and new_text are required");
		}

		try {
			File target = pathGuard.resolve(path);
			String text = Files.readString(target.toPath(), StandardCharsets.UTF_8);
			if (!text.contains(oldText)) {
				return new ToolResult("Error: text not found in " + path);
			}
			Files.writeString(target.toPath(), text.replaceFirst(java.util.regex.Pattern.quote(oldText),
					java.util.regex.Matcher.quoteReplacement(newText)), StandardCharsets.UTF_8);
			return new ToolResult("Edited " + path);
		}
		catch (IOException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
