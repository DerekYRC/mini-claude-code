package org.miniclaudecode.background;

import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台任务生命周期管理器。
 *
 * 职责：启动 daemon 线程执行慢操作，追踪每个后台任务的状态，
 * 收集已完成任务并格式化为 &lt;task_notification&gt; 通知。
 *
 * 线程安全：使用 ConcurrentHashMap，不加显式锁。
 */
public class BackgroundTasks {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> results = new ConcurrentHashMap<>();

    /**
     * 启动后台任务，立即返回 bg_id。
     *
     * @param block    工具调用 block
     * @param registry 工具注册中心（用于查找和执行工具）
     * @return 后台任务 ID（格式 bg_0001）
     */
    public String start(ToolUseBlock block, ToolRegistry registry) {
        int idx = counter.incrementAndGet();
        String bgId = String.format("bg_%04d", idx);
        String command = block.getInput() != null ? block.getInput().getString("command") : block.getName();
        if (command != null && command.length() > 80) {
            command = command.substring(0, 80);
        }

        BackgroundTask task = new BackgroundTask(bgId, block.getId(), command, "running");
        tasks.put(bgId, task);

        // daemon 线程：进程退出时自动终止
        Thread worker = new Thread(() -> executeInBackground(bgId, block, registry));
        worker.setDaemon(true);
        worker.setName("bg-" + bgId);
        worker.start();

        System.out.println("  [background] dispatched " + bgId + ": " + command);
        return bgId;
    }

    /**
     * 收集所有已完成任务的通知，从追踪 map 中移除。
     *
     * @return &lt;task_notification&gt; 格式的 XML 文本列表
     */
    public List<String> collectNotifications() {
        List<String> notifications = new ArrayList<>();
        // 先收集已完成的 bgId，避免在遍历时修改 map
        List<String> readyIds = new ArrayList<>();
        for (Map.Entry<String, BackgroundTask> entry : tasks.entrySet()) {
            String status = entry.getValue().getStatus();
            if ("completed".equals(status) || "timeout".equals(status) || "error".equals(status)) {
                readyIds.add(entry.getKey());
            }
        }
        for (String bgId : readyIds) {
            BackgroundTask task = tasks.remove(bgId);
            String output = results.remove(bgId);
            if (task == null) continue;
            if (output == null) output = "(no output)";
            String summary = output.length() > 500 ? output.substring(0, 500) : output;
            String notification =
                "<task_notification>\n"
                + "  <task_id>" + bgId + "</task_id>\n"
                + "  <status>" + task.getStatus() + "</status>\n"
                + "  <command>" + task.getCommand() + "</command>\n"
                + "  <summary>" + escapeXml(summary) + "</summary>\n"
                + "</task_notification>";
            notifications.add(notification);
            System.out.println("  [background done] " + bgId + ": "
                    + task.getCommand() + " (" + output.length() + " chars)");
        }
        return notifications;
    }

    private void executeInBackground(String bgId, ToolUseBlock block, ToolRegistry registry) {
        try {
            Tool tool = registry.find(block.getName());
            if (tool == null) {
                tasks.get(bgId).setStatus("error");
                results.put(bgId, "Unknown tool: " + block.getName());
                return;
            }
            ToolResult result = tool.execute(block.getInput());
            tasks.get(bgId).setStatus("completed");
            results.put(bgId, result.getContent() != null ? result.getContent() : "(no output)");
        } catch (Exception e) {
            BackgroundTask task = tasks.get(bgId);
            if (task != null) {
                task.setStatus("error");
            }
            results.put(bgId, "Error: " + e.getMessage());
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
