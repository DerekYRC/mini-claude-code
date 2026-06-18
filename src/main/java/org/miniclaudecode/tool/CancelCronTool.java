package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.cron.CronScheduler;

/**
 * cancel_cron 工具：按 ID 取消一个 cron 任务。
 */
public class CancelCronTool implements Tool {

    private final CronScheduler scheduler;

    public CancelCronTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /*
     * {
     *   "name": "cancel_cron",
     *   "description": "Cancel a cron job by ID.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {
     *       "job_id": {"type": "string"}
     *     },
     *     "required": ["job_id"]
     *   }
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("job_id", new JSONObject()
                        .fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("job_id"));
        return new ToolDefinition("cancel_cron",
                "Cancel a cron job by ID.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        try {
            String jobId = input != null ? input.getString("job_id") : null;
            if (jobId == null || jobId.isBlank()) {
                return new ToolResult("Error: job_id is required");
            }
            return new ToolResult(scheduler.cancel(jobId));
        } catch (Exception e) {
            return new ToolResult("Error: " + e.getMessage());
        }
    }
}
