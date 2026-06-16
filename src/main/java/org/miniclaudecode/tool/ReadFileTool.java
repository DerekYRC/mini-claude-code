package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取 workdir 内 UTF-8 文本文件的工具。
 */
public class ReadFileTool implements Tool {

	private final File workdir;

	private final PathGuard pathGuard;

	public ReadFileTool(File workdir) {
		this.workdir = workdir;
		this.pathGuard = new PathGuard(workdir);
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("path", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "File path relative to the workdir"))
				.fluentPut("limit", new JSONObject()
						.fluentPut("type", "integer")
						.fluentPut("description", "Optional max number of lines to return"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("path"));
		return new ToolDefinition("read_file", "Read a UTF-8 text file from the workdir", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String path = input == null ? "" : input.getString("path");
		if (path == null || path.isBlank()) {
			return new ToolResult("Error: No path provided");
		}

		try {
			File target = pathGuard.resolve(path);
			Integer limit = input.getInteger("limit");
			List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
			if (limit != null && limit > 0 && limit < lines.size()) {
				// limit 是教学版的简单输出控制，避免一次把大文件全部塞回上下文。
				List<String> limited = new ArrayList<>(lines.subList(0, limit));
				limited.add("... (" + (lines.size() - limit) + " more lines)");
				lines = limited;
			}
			return new ToolResult(String.join("\n", lines));
		}
		catch (IOException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
