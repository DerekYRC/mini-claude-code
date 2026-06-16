package org.miniclaudecode.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具分发 Map。
 *
 * s02 的重点是：主循环不关心有多少工具，只按 tool_use.name 找到对应 handler。
 * 新增工具时，只需要 register 一个 Tool，不需要改 AgentLoop。
 */
public class ToolRegistry {

	private final Map<String, Tool> tools = new LinkedHashMap<>();

	public ToolRegistry register(Tool tool) {
		// 工具名就是 dispatch key，必须和 ToolDefinition 暴露给模型的 name 保持一致。
		tools.put(tool.getDefinition().getName(), tool);
		return this;
	}

	public Tool find(String name) {
		// 主循环只调用 find；找不到时返回 null，由 AgentLoop 转成 Unknown tool。
		return tools.get(name);
	}

	public List<ToolDefinition> definitions() {
		List<ToolDefinition> definitions = new ArrayList<>();
		for (Tool tool : tools.values()) {
			definitions.add(tool.getDefinition());
		}
		return definitions;
	}
}
