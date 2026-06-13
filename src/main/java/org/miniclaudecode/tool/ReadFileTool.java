package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ReadFileTool implements Tool {

	private final File workdir;

	public ReadFileTool(File workdir) {
		this.workdir = workdir;
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
			File target = new File(workdir, path).getCanonicalFile();
			File root = workdir.getCanonicalFile();
			if (!target.toPath().startsWith(root.toPath())) {
				return new ToolResult("Error: Path escapes workspace: " + path);
			}

			Integer limit = input.getInteger("limit");
			List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
			if (limit != null && limit > 0 && limit < lines.size()) {
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
