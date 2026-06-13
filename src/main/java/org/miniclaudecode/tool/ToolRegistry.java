package org.miniclaudecode.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

	private final Map<String, Tool> tools = new LinkedHashMap<>();

	public ToolRegistry register(Tool tool) {
		tools.put(tool.getDefinition().getName(), tool);
		return this;
	}

	public Tool find(String name) {
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
