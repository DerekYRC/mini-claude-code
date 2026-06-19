package org.miniclaudecode.team;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * s13 文件邮箱。
 *
 * 发送消息就是追加一行 JSONL；读取 inbox 后删除文件，表示消息已消费。
 * 教学版用 synchronized 覆盖同进程并发，不处理跨进程文件锁。
 */
public class MessageBus {

    private final File mailboxDir;

    public MessageBus(File workdir) {
        this.mailboxDir = new File(workdir, ".mailboxes");
        FileUtil.mkdir(mailboxDir);
    }

    public synchronized void send(String from, String to, String content) {
        send(from, to, content, "message");
    }

    public synchronized void send(String from, String to, String content, String type) {
        send(from, to, content, type, new JSONObject());
    }

    public synchronized void send(String from, String to, String content,
            String type, JSONObject metadata) {
        TeamMessage message = new TeamMessage(from, to, type, content,
                System.currentTimeMillis(), metadata);
        FileUtil.appendUtf8String(JSON.toJSONString(message) + "\n", inboxFile(to));
        System.out.println("  [bus] " + from + " -> " + to
                + ": (" + type + ") " + preview(content));
    }

    public synchronized List<TeamMessage> readInbox(String agent) {
        File inbox = inboxFile(agent);
        if (!inbox.exists()) {
            return new ArrayList<>();
        }
        List<TeamMessage> messages = new ArrayList<>();
        String text = FileUtil.readUtf8String(inbox);
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                messages.add(JSON.parseObject(line, TeamMessage.class));
            }
        }
        FileUtil.del(inbox);
        return messages;
    }

    public String formatInbox(List<TeamMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (TeamMessage message : messages) {
            JSONObject metadata = message.getMetadata();
            String requestId = metadata == null ? "" : metadata.getString("request_id");
            sb.append("From ").append(message.getFrom())
                    .append(" [").append(message.getType());
            if (requestId != null && !requestId.isBlank()) {
                sb.append(" req:").append(requestId);
            }
            sb.append("]: ")
                    .append(message.getContent())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private File inboxFile(String agent) {
        return new File(mailboxDir, agent + ".jsonl");
    }

    private String preview(String content) {
        if (content == null || content.length() <= 60) {
            return content;
        }
        return content.substring(0, 60) + "...";
    }
}
