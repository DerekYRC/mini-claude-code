package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.protocol.ProtocolService;

/**
 * 队友提交计划给 Lead 审批。
 */
public class SubmitPlanTool implements Tool {

    private final ProtocolService protocol;

    private final String fromName;

    public SubmitPlanTool(ProtocolService protocol, String fromName) {
        this.protocol = protocol;
        this.fromName = fromName;
    }

    /*
     * {
     *   "name": "submit_plan",
     *   "description": "Submit a plan for Lead approval.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {"plan": {"type": "string"}},
     *     "required": ["plan"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("plan", new JSONObject().fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("plan"));
        return new ToolDefinition("submit_plan",
                "Submit a plan for Lead approval.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        String plan = input == null ? "" : input.getString("plan");
        if (plan == null || plan.isBlank()) {
            return new ToolResult("Error: plan is required");
        }
        return new ToolResult(protocol.submitPlan(fromName, plan.trim()));
    }
}
