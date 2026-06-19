package org.miniclaudecode.team;

import com.alibaba.fastjson.JSONObject;

/**
 * 文件邮箱里的一条消息。
 *
 * 教学版只保留通信所需字段，不设计完整协议；s16 再引入固定请求-回复格式。
 */
public class TeamMessage {

    private String from;

    private String to;

    private String type;

    private String content;

    private long timestamp;

    private JSONObject metadata;

    public TeamMessage() {
    }

    public TeamMessage(String from, String to, String type, String content, long timestamp) {
        this(from, to, type, content, timestamp, new JSONObject());
    }

    public TeamMessage(String from, String to, String type, String content,
            long timestamp, JSONObject metadata) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.content = content;
        this.timestamp = timestamp;
        this.metadata = metadata == null ? new JSONObject() : metadata;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public JSONObject getMetadata() {
        return metadata;
    }

    public void setMetadata(JSONObject metadata) {
        this.metadata = metadata;
    }
}
