package org.miniclaudecode.protocol;

/**
 * 一条协议请求的状态。
 *
 * requestId 把 request 和 response 串起来；status 只在内存里追踪。
 */
public class ProtocolState {

    private String requestId;

    private String type;

    private String sender;

    private String target;

    private String status;

    private String payload;

    private long createdAt;

    public ProtocolState() {
    }

    public ProtocolState(String requestId, String type, String sender,
            String target, String status, String payload, long createdAt) {
        this.requestId = requestId;
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.status = status;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
