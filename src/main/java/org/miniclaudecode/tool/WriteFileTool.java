package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 写入 workdir 内 UTF-8 文本文件的工具。
 */
public class WriteFileTool implements Tool {

	private final PathGuard pathGuard;

	public WriteFileTool(File workdir) {
		this.pathGuard = new PathGuard(workdir);
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("path", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "File path relative to the workdir"))
				.fluentPut("content", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Content to write"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("path").fluentAdd("content"));
		return new ToolDefinition("write_file", "Write content to a UTF-8 file in the workdir", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String path = input == null ? "" : input.getString("path");
		String content = input == null ? null : input.getString("content");
		if (path == null || path.isBlank()) {
			return new ToolResult("Error: No path provided");
		}
		if (content == null) {
			return new ToolResult("Error: No content provided");
		}

		try {
			File target = pathGuard.resolve(path);
			File parent = target.getParentFile();
			if (parent != null) {
				// 自动创建父目录，让 demo prompt 可以直接写入 target/... 这类路径。
				Files.createDirectories(parent.toPath());
			}
			Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
			return new ToolResult("Wrote " + content.getBytes(StandardCharsets.UTF_8).length + " bytes to " + path);
		}
		catch (IOException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
