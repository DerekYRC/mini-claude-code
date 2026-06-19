package org.miniclaudecode.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 保存已连接的 MCP server。
 *
 * 真实产品会从配置加载 server；教学版直接按名字连接 mock server。
 */
public class McpManager {

	private final Map<String, McpClient> clients = new LinkedHashMap<>();

	public String connect(String name) {
		if (name == null || name.isBlank()) {
			return "Error: MCP server name is required. Available: " + availableNames();
		}
		String key = name.trim();
		if (clients.containsKey(key)) {
			return "MCP server '" + key + "' already connected";
		}

		McpClient client = MockMcpServers.create(key);
		if (client == null) {
			return "Unknown MCP server '" + key + "'. Available: " + availableNames();
		}
		clients.put(key, client);

		List<String> discovered = new ArrayList<>();
		for (McpToolDefinition tool : client.getTools()) {
			discovered.add(McpToolName.prefixed(client.getName(), tool.getName()));
		}
		return "Connected to MCP server '" + key + "'. Discovered tools: "
				+ String.join(", ", discovered);
	}

	public Collection<McpClient> connectedClients() {
		return clients.values();
	}

	public String connectedServerNames() {
		if (clients.isEmpty()) {
			return "(none)";
		}
		return clients.keySet().stream().collect(Collectors.joining(", "));
	}

	private String availableNames() {
		return String.join(", ", MockMcpServers.availableNames());
	}
}
