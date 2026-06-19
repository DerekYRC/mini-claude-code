package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.protocol.ProtocolService;

/**
 * Lead 请求队友先提交计划。
 */
public class RequestPlanTool implements Tool {

    private final ProtocolService protocol;

    public RequestPlanTool(ProtocolService protocol) {
        this.protocol = protocol;
    }

    /*
     * {
     *   "name": "request_plan",
     *   "description": "Ask a teammate to submit a plan for review.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {
     *       "teammate": {"type": "string"},
     *       "task": {"type": "string"}
     *     },
     *     "required": ["teammate", "task"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("teammate", new JSONObject().fluentPut("type", "string"))
                .fluentPut("task", new JSONObject().fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("teammate").fluentAdd("task"));
        return new ToolDefinition("request_plan",
                "Ask a teammate to submit a plan for review.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        String teammate = input == null ? "" : input.getString("teammate");
        String task = input == null ? "" : input.getString("task");
        if (teammate == null || teammate.isBlank()) {
            return new ToolResult("Error: teammate is required");
        }
        if (task == null || task.isBlank()) {
            return new ToolResult("Error: task is required");
        }
        return new ToolResult(protocol.requestPlan(teammate.trim(), task.trim()));
    }
}
