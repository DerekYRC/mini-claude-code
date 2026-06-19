package org.miniclaudecode.mcp;

import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学版 MCP client。
 *
 * 真实 MCP client 会通过 transport 发送 tools/list 和 tools/call；
 * 这里用内存 handler 模拟外部 server，突出“发现工具”和“调用工具”两件事。
 */
public class McpClient {

	public interface Handler {

		String handle(JSONObject input);
	}

	private final String name;

	private List<McpToolDefinition> tools = Collections.emptyList();

	private Map<String, Handler> handlers = new LinkedHashMap<>();

	public McpClient(String name) {
		this.name = name;
	}

	public void register(List<McpToolDefinition> tools, Map<String, Handler> handlers) {
		this.tools = tools == null ? Collections.emptyList() : tools;
		this.handlers = handlers == null ? new LinkedHashMap<>() : handlers;
	}

	public String callTool(String toolName, JSONObject input) {
		Handler handler = handlers.get(toolName);
		if (handler == null) {
			return "MCP error: unknown tool '" + toolName + "'";
		}
		try {
			return handler.handle(input == null ? new JSONObject() : input);
		}
		catch (Exception e) {
			return "MCP error: " + e.getMessage();
		}
	}

	public String getName() {
		return name;
	}

	public List<McpToolDefinition> getTools() {
		return tools;
	}
}
