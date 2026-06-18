package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.cron.CronJob;
import org.miniclaudecode.cron.CronScheduler;

import java.util.List;

/**
 * list_crons 工具：列出所有已注册的 cron 任务。
 */
public class ListCronsTool implements Tool {

    private final CronScheduler scheduler;

    public ListCronsTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /*
     * {
     *   "name": "list_crons",
     *   "description": "List all registered cron jobs.",
     *   "input_schema": {"type": "object", "properties": {}}
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", new JSONObject());
        return new ToolDefinition("list_crons",
                "List all registered cron jobs.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        List<CronJob> jobs = scheduler.list();
        if (jobs.isEmpty()) {
            return new ToolResult("No cron jobs. Use schedule_cron to add one.");
        }
        StringBuilder sb = new StringBuilder();
        for (CronJob j : jobs) {
            String tag = j.isRecurring() ? "recurring" : "one-shot";
            String dur = j.isDurable() ? "durable" : "session";
            sb.append("  ").append(j.getId()).append(": '").append(j.getCron())
                    .append("' → ").append(j.getPrompt())
                    .append(" [").append(tag).append(", ").append(dur).append("]\n");
        }
        return new ToolResult(sb.toString());
    }
}
