package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * s01 唯一真实工具：执行 shell 命令。
 *
 * 本章故意不做权限判断，让读者先看清楚“模型请求工具 -> Java 执行工具 -> 结果回给模型”的闭环。
 */
public class BashTool implements Tool {

	private final File workdir;

	public BashTool(File workdir) {
		this.workdir = workdir;
	}

	@Override
	public ToolDefinition getDefinition() {
		JSONObject properties = new JSONObject()
				.fluentPut("command", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "Shell command to run"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("command"));
		return new ToolDefinition("bash", "Run a shell command and return stdout/stderr", schema);
	}

	@Override
	public ToolResult execute(JSONObject input) {
		String command = input == null ? "" : input.getString("command");
		if (command == null || command.isBlank()) {
			return new ToolResult("No command provided");
		}

		try {
			// bash 工具的工作目录和 system prompt 中告诉模型的 workdir 保持一致。
			Process process = new ProcessBuilder("/bin/sh", "-c", command)
					.directory(workdir)
					.redirectErrorStream(true)
					.start();
			String output = readOutput(process);
			int exitCode = process.waitFor();
			return new ToolResult("exit_code=" + exitCode + "\n" + output);
		}
		catch (IOException e) {
			return new ToolResult("Command failed to start: " + e.getMessage());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new ToolResult("Command interrupted");
		}
	}

	private String readOutput(Process process) throws IOException {
		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append('\n');
			}
		}
		return output.toString();
	}
}
