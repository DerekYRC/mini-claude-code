package org.miniclaudecode.cron;

/**
 * s12 cron 任务数据类。
 *
 * 字段对齐参考项目 s14 CronJob dataclass。
 */
public class CronJob {

    private String id;         // cron job ID，例如 cron_2f4a91bc
    private String cron;       // 五段式 cron 表达式
    private String prompt;     // 触发后注入给 Agent 的文本
    private boolean recurring; // true=周期任务，false=一次性
    private boolean durable;   // true=持久化到 .scheduled_tasks.json

    public CronJob() {
    }

    public CronJob(String id, String cron, String prompt, boolean recurring, boolean durable) {
        this.id = id;
        this.cron = cron;
        this.prompt = prompt;
        this.recurring = recurring;
        this.durable = durable;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    public boolean isDurable() { return durable; }
    public void setDurable(boolean durable) { this.durable = durable; }
}
