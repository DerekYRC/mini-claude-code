package org.miniclaudecode.mcp;

import org.miniclaudecode.tool.ConnectMcpTool;
import org.miniclaudecode.tool.McpTool;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 每轮把内置工具和已连接 MCP 工具组装成一个工具池。
 */
public class McpToolPool {

	private final List<Tool> builtinTools;

	private final McpManager mcpManager;

	public McpToolPool(List<Tool> builtinTools, McpManager mcpManager) {
		this.builtinTools = builtinTools;
		this.mcpManager = mcpManager;
	}

	public ToolRegistry assemble() {
		ToolRegistry registry = new ToolRegistry();
		Set<String> names = new LinkedHashSet<>();

		for (Tool tool : builtinTools) {
			String name = tool.getDefinition().getName();
			if (names.add(name)) {
				registry.register(tool);
			}
		}

		ConnectMcpTool connectMcpTool = new ConnectMcpTool(mcpManager);
		String connectName = connectMcpTool.getDefinition().getName();
		if (names.add(connectName)) {
			registry.register(connectMcpTool);
		}

		for (McpClient client : mcpManager.connectedClients()) {
			for (McpToolDefinition definition : client.getTools()) {
				String name = McpToolName.prefixed(client.getName(), definition.getName());
				if (names.add(name)) {
					registry.register(new McpTool(client, definition));
				}
				else {
					// 重复工具名跳过，避免后注册工具覆盖已有工具。
					System.out.println("[mcp] skipped duplicate tool: " + name);
				}
			}
		}
		return registry;
	}
}
