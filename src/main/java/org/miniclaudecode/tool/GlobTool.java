package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 workdir 内按 glob pattern 查找文件的工具。
 */
public class GlobTool implements Tool {

	private final File workdir;

	public GlobTool(File workdir) {
		this.workdir = workdir;
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("pattern", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Glob pattern relative to the workdir"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("pattern"));
		return new ToolDefinition("glob", "Find files matching a glob pattern in the workdir", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String pattern = input == null ? "" : input.getString("pattern");
		if (pattern == null || pattern.isBlank()) {
			return new ToolResult("Error: No pattern provided");
		}

		try {
			Path root = workdir.getCanonicalFile().toPath();
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
			List<String> matches = new ArrayList<>();
			try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
				// glob 只负责列出匹配文件，不读取文件内容；读取交给 read_file。
				paths.filter(Files::isRegularFile)
						.map(root::relativize)
						.filter(matcher::matches)
						.map(Path::toString)
						.sorted()
						.forEach(matches::add);
			}
			return new ToolResult(matches.isEmpty() ? "(no matches)" : String.join("\n", matches));
		}
		catch (IOException | IllegalArgumentException e) {
			return new ToolResult("Error: " + e.getMessage());
		}
	}
}
