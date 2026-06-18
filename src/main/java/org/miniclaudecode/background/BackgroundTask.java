package org.miniclaudecode.background;

/**
 * 一条后台任务的状态快照。
 *
 * 不持久化，只在当前进程生命周期内存在。
 */
public class BackgroundTask {

    private String bgId;         // 后台任务 ID，格式 bg_0001
    private String toolUseId;    // 原始 tool_use block 的 id
    private String command;      // 执行的命令文本（截取前 80 字符）
    private String status;       // running | completed | timeout | error

    public BackgroundTask() {
    }

    public BackgroundTask(String bgId, String toolUseId, String command, String status) {
        this.bgId = bgId;
        this.toolUseId = toolUseId;
        this.command = command;
        this.status = status;
    }

    public String getBgId() { return bgId; }
    public void setBgId(String bgId) { this.bgId = bgId; }

    public String getToolUseId() { return toolUseId; }
    public void setToolUseId(String toolUseId) { this.toolUseId = toolUseId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
