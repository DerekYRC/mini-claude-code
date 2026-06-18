package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.cron.CronScheduler;

/**
 * schedule_cron 工具：注册一个 cron 定时任务。
 */
public class ScheduleCronTool implements Tool {

    private final CronScheduler scheduler;

    public ScheduleCronTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /*
     * {
     *   "name": "schedule_cron",
     *   "description": "Schedule a cron job. cron is 5-field: min hour dom month dow.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {
     *       "cron": {"type": "string", "description": "5-field cron expression"},
     *       "prompt": {"type": "string", "description": "Message to inject when fired"},
     *       "recurring": {"type": "boolean", "description": "True=recurring, False=one-shot"},
     *       "durable": {"type": "boolean", "description": "True=persist to disk"}
     *     },
     *     "required": ["cron", "prompt"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("cron", new JSONObject()
                        .fluentPut("type", "string")
                        .fluentPut("description", "5-field cron expression"))
                .fluentPut("prompt", new JSONObject()
                        .fluentPut("type", "string")
                        .fluentPut("description", "Message to inject when fired"))
                .fluentPut("recurring", new JSONObject()
                        .fluentPut("type", "boolean")
                        .fluentPut("description", "True=recurring, False=one-shot"))
                .fluentPut("durable", new JSONObject()
                        .fluentPut("type", "boolean")
                        .fluentPut("description", "True=persist to disk"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("cron").fluentAdd("prompt"));
        return new ToolDefinition("schedule_cron",
                "Schedule a cron job. cron is 5-field: min hour dom month dow.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        try {
            String cron = input != null ? input.getString("cron") : null;
            String prompt = input != null ? input.getString("prompt") : null;
            if (cron == null || cron.isBlank()) {
                return new ToolResult("Error: cron is required");
            }
            if (prompt == null || prompt.isBlank()) {
                return new ToolResult("Error: prompt is required");
            }
            boolean recurring = input.getBoolean("recurring") == null
                    || Boolean.TRUE.equals(input.getBoolean("recurring"));
            boolean durable = input.getBoolean("durable") == null
                    || Boolean.TRUE.equals(input.getBoolean("durable"));
            return new ToolResult(scheduler.schedule(cron, prompt, recurring, durable));
        } catch (Exception e) {
            return new ToolResult("Error: " + e.getMessage());
        }
    }
}
