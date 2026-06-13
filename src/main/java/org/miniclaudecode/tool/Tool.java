package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;

public interface Tool {

	ToolDefinition getDefinition();

	ToolResult execute(JSONObject input);
}
