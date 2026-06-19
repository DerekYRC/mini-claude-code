package org.miniclaudecode.protocol;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.team.MessageBus;
import org.miniclaudecode.team.TeamMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * s14 协议状态机。
 *
 * 教学版只在进程内保存 pendingRequests；重启后协议状态丢失。
 */
public class ProtocolService {

    private final MessageBus bus;

    private final AtomicInteger sequence = new AtomicInteger();

    private final Map<String, ProtocolState> pendingRequests = new ConcurrentHashMap<>();

    public ProtocolService(MessageBus bus) {
        this.bus = bus;
    }

    public String requestShutdown(String teammate) {
        String requestId = newRequestId();
        pendingRequests.put(requestId, new ProtocolState(requestId, "shutdown",
                "lead", teammate, "pending", "", System.currentTimeMillis()));
        bus.send("lead", teammate, "Please shut down gracefully.",
                "shutdown_request", metadata(requestId));
        System.out.println("  [protocol] shutdown_request -> " + teammate
                + " (" + requestId + ")");
        return "Shutdown request sent to " + teammate + " (req: " + requestId + ")";
    }

    public String requestPlan(String teammate, String task) {
        bus.send("lead", teammate, "Please submit a plan for: " + task, "message");
        return "Asked " + teammate + " to submit a plan";
    }

    public String submitPlan(String fromName, String plan) {
        String requestId = newRequestId();
        pendingRequests.put(requestId, new ProtocolState(requestId, "plan_approval",
                fromName, "lead", "pending", plan, System.currentTimeMillis()));
        bus.send(fromName, "lead", plan, "plan_approval_request", metadata(requestId));
        return "Plan submitted (" + requestId + "). Waiting for approval...";
    }

    public String reviewPlan(String requestId, boolean approve, String feedback) {
        ProtocolState state = pendingRequests.get(requestId);
        if (state == null) {
            return "Request " + requestId + " not found";
        }
        if (!"pending".equals(state.getStatus())) {
            return "Request " + requestId + " already " + state.getStatus();
        }
        state.setStatus(approve ? "approved" : "rejected");
        JSONObject metadata = metadata(requestId);
        metadata.put("approve", approve);
        String content = feedback == null || feedback.isBlank()
                ? (approve ? "Approved" : "Rejected")
                : feedback;
        bus.send("lead", state.getSender(), content,
                "plan_approval_response", metadata);
        System.out.println("  [protocol] plan " + (approve ? "approved" : "rejected")
                + " (" + requestId + ")");
        return "Plan " + (approve ? "approved" : "rejected") + " (" + requestId + ")";
    }

    public List<TeamMessage> consumeLeadInbox() {
        List<TeamMessage> messages = bus.readInbox("lead");
        for (TeamMessage message : messages) {
            JSONObject metadata = message.getMetadata();
            if (metadata == null) {
                continue;
            }
            String requestId = metadata.getString("request_id");
            String type = message.getType();
            if (requestId != null && type != null && type.endsWith("_response")) {
                matchResponse(type, requestId, metadata.getBooleanValue("approve"));
            }
        }
        return messages;
    }

    public boolean isProtocolMessage(TeamMessage message) {
        String type = message.getType();
        return "shutdown_request".equals(type) || "plan_approval_response".equals(type);
    }

    public boolean handleTeammateProtocolMessage(String name, TeamMessage message,
            List<Message> messages) {
        String type = message.getType();
        JSONObject metadata = message.getMetadata();
        String requestId = metadata == null ? "" : metadata.getString("request_id");
        if ("shutdown_request".equals(type)) {
            JSONObject responseMeta = metadata(requestId);
            responseMeta.put("approve", true);
            bus.send(name, "lead", "Shutting down gracefully.",
                    "shutdown_response", responseMeta);
            System.out.println("  [protocol] " + name
                    + " approved shutdown (" + requestId + ")");
            return true;
        }
        if ("plan_approval_response".equals(type)) {
            boolean approve = metadata != null && metadata.getBooleanValue("approve");
            String text = approve
                    ? "[Plan approved] Proceed with the task."
                    : "[Plan rejected] Feedback: " + message.getContent();
            messages.add(Message.user(text));
        }
        return false;
    }

    private void matchResponse(String responseType, String requestId, boolean approve) {
        ProtocolState state = pendingRequests.get(requestId);
        if (state == null) {
            System.out.println("  [protocol] unknown request_id: " + requestId);
            return;
        }
        if ("shutdown".equals(state.getType()) && !"shutdown_response".equals(responseType)) {
            System.out.println("  [protocol] type mismatch: expected shutdown_response, got "
                    + responseType);
            return;
        }
        if ("plan_approval".equals(state.getType())
                && !"plan_approval_response".equals(responseType)) {
            System.out.println("  [protocol] type mismatch: expected plan_approval_response, got "
                    + responseType);
            return;
        }
        if (!"pending".equals(state.getStatus())) {
            System.out.println("  [protocol] " + requestId + " already "
                    + state.getStatus() + ", ignoring duplicate");
            return;
        }
        state.setStatus(approve ? "approved" : "rejected");
        System.out.println("  [protocol] " + state.getType() + " "
                + state.getStatus() + " (" + requestId + ")");
    }

    private String newRequestId() {
        return String.format("req_%06d", sequence.incrementAndGet());
    }

    private JSONObject metadata(String requestId) {
        return new JSONObject().fluentPut("request_id", requestId);
    }
}
