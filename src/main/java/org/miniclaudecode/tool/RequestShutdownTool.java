package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.protocol.ProtocolService;

/**
 * Lead 请求队友体面关机。
 */
public class RequestShutdownTool implements Tool {

    private final ProtocolService protocol;

    public RequestShutdownTool(ProtocolService protocol) {
        this.protocol = protocol;
    }

    /*
     * {
     *   "name": "request_shutdown",
     *   "description": "Request a teammate to shut down gracefully.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {"teammate": {"type": "string"}},
     *     "required": ["teammate"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("teammate", new JSONObject().fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("teammate"));
        return new ToolDefinition("request_shutdown",
                "Request a teammate to shut down gracefully.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        String teammate = input == null ? "" : input.getString("teammate");
        if (teammate == null || teammate.isBlank()) {
            return new ToolResult("Error: teammate is required");
        }
        return new ToolResult(protocol.requestShutdown(teammate.trim()));
    }
}
