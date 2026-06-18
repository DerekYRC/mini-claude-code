package org.miniclaudecode.cron;

import cn.hutool.cron.CronUtil;
import cn.hutool.cron.pattern.CronPattern;
import cn.hutool.cron.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * s12 cron 调度器。
 *
 * 职责：注册/取消 cron 任务（委托给 Hutool CronUtil），
 * 管理内存 job map，触发时回调 Consumer。
 *
 * 教学版不引入队列和 queue processor，cron 触发直接在回调中
 * 拿锁调用 Agent。Hutool 负责 cron 解析和触发时间判断。
 */
public class CronScheduler {

    private final CronStore store;
    private final Consumer<CronJob> onFire;
    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();

    /**
     * @param store  持久化存储
     * @param onFire cron 触发回调（在 Hutool 定时线程中执行）
     */
    public CronScheduler(CronStore store, Consumer<CronJob> onFire) {
        this.store = store;
        this.onFire = onFire;
    }

    /**
     * 从磁盘加载 durable job 并注册到 Hutool，然后启动 cron 调度线程。
     */
    public void start() {
        List<CronJob> loaded = store.load();
        for (CronJob job : loaded) {
            String err = validateCron(job.getCron());
            if (err != null) {
                System.err.println("  [cron] skipping invalid durable job "
                        + job.getId() + ": " + err);
                continue;
            }
            jobs.put(job.getId(), job);
            CronUtil.schedule(job.getId(), job.getCron(), new CronTask(job.getId()));
            System.out.println("  [cron] loaded durable job " + job.getId()
                    + " '" + job.getCron() + "'");
        }
        if (!loaded.isEmpty()) {
            System.out.println("  [cron] loaded " + loaded.size() + " durable job(s)");
        }
        CronUtil.setMatchSecond(false);
        CronUtil.start();
        System.out.println("  [cron] scheduler started");
    }

    /**
     * 停止 cron 调度线程。
     */
    public void stop() {
        CronUtil.stop();
    }

    /**
     * 注册一个新的 cron 任务。
     *
     * @return CronJob，如果 cron 表达式无效则返回 null 并附带错误消息
     */
    public String schedule(String cron, String prompt, boolean recurring, boolean durable) {
        String err = validateCron(cron);
        if (err != null) {
            return "Error: " + err;
        }
        String id = "cron_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000000));
        while (id.length() < 10) {
            id = "cron_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000000));
        }
        id = id.length() > 14 ? id.substring(0, 14) : id;

        CronJob job = new CronJob(id, cron, prompt, recurring, durable);
        jobs.put(id, job);
        CronUtil.schedule(id, cron, new CronTask(id));

        if (durable) {
            store.save(new ArrayList<>(jobs.values()));
        }
        System.out.println("  [cron register] " + id + " '" + cron + "' → "
                + (prompt.length() > 40 ? prompt.substring(0, 40) : prompt));
        return "Scheduled " + id + ": '" + cron + "' → " + prompt;
    }

    /**
     * 取消一个 cron 任务。
     */
    public String cancel(String jobId) {
        CronJob removed = jobs.remove(jobId);
        if (removed == null) {
            return "Job " + jobId + " not found";
        }
        CronUtil.remove(jobId);
        if (removed.isDurable()) {
            store.save(new ArrayList<>(jobs.values()));
        }
        System.out.println("  [cron cancel] " + jobId);
        return "Cancelled " + jobId;
    }

    /**
     * 列出所有已注册的 cron 任务。
     */
    public List<CronJob> list() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * 校验 cron 表达式（5 段式）。
     *
     * @return 错误消息，null 表示合法
     */
    private String validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return "cron expression is required";
        }
        String[] fields = cron.trim().split("\\s+");
        if (fields.length != 5) {
            return "Expected 5 fields, got " + fields.length + ": " + cron;
        }
        try {
            CronPattern.of(cron);
        } catch (Exception e) {
            return "Invalid cron expression: " + e.getMessage();
        }
        return null;
    }

    /**
     * Hutool Task 包装：触发时从 jobs map 中查找 CronJob 并回调 onFire。
     */
    private class CronTask implements Task {
        private final String jobId;

        CronTask(String jobId) {
            this.jobId = jobId;
        }

        @Override
        public void execute() {
            CronJob job = jobs.get(jobId);
            if (job == null) {
                return;
            }
            System.out.println("  [cron fire] " + jobId + " → "
                    + (job.getPrompt().length() > 40
                    ? job.getPrompt().substring(0, 40) : job.getPrompt()));
            try {
                onFire.accept(job);
            } catch (Exception e) {
                System.err.println("  [cron error] " + jobId + ": " + e.getMessage());
            }
            // 一次性任务触发后自动取消
            if (!job.isRecurring()) {
                cancel(jobId);
            }
        }
    }
}
