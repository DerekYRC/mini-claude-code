package org.miniclaudecode.cron;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * s12 cron 持久化存储。
 *
 * 只负责 .scheduled_tasks.json 的读写，不涉及调度逻辑。
 * 只保存 durable=true 的任务。
 */
public class CronStore {

    private final File file;

    public CronStore(File workdir) {
        this.file = new File(workdir, ".scheduled_tasks.json");
    }

    /**
     * 保存所有 durable job 到文件。
     */
    public void save(List<CronJob> jobs) {
        List<CronJob> durable = new ArrayList<>();
        for (CronJob j : jobs) {
            if (j.isDurable()) {
                durable.add(j);
            }
        }
        try {
            Files.writeString(file.toPath(),
                    JSON.toJSONString(durable, true),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("  [cron] failed to save durable jobs: " + e.getMessage());
        }
    }

    /**
     * 从文件加载所有 durable job。
     */
    public List<CronJob> load() {
        List<CronJob> jobs = new ArrayList<>();
        if (!file.exists()) {
            return jobs;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JSONArray arr = JSON.parseArray(content);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    CronJob job = arr.getObject(i, CronJob.class);
                    if (job.getId() != null && job.getCron() != null) {
                        jobs.add(job);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  [cron] failed to load durable jobs: " + e.getMessage());
        }
        return jobs;
    }
}
