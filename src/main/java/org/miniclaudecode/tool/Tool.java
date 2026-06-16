package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;

/**
 * 所有工具的最小接口：既能描述给模型，也能在 Java 侧真正执行。
 */
public interface Tool {

	ToolDefinition getDefinition();

	ToolResult execute(JSONObject input);
}
