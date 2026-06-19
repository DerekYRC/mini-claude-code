package org.miniclaudecode.mcp;

import java.util.regex.Pattern;

/**
 * MCP 工具名规范化。
 *
 * 对齐参考实现：mcp__server__tool，非 [a-zA-Z0-9_-] 字符替换为下划线。
 */
public final class McpToolName {

	private static final Pattern DISALLOWED = Pattern.compile("[^a-zA-Z0-9_-]");

	private McpToolName() {
	}

	public static String normalize(String name) {
		if (name == null || name.isBlank()) {
			return "unnamed";
		}
		return DISALLOWED.matcher(name).replaceAll("_");
	}

	public static String prefixed(String serverName, String toolName) {
		return "mcp__" + normalize(serverName) + "__" + normalize(toolName);
	}
}
