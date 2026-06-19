package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.team.MessageBus;
import org.miniclaudecode.team.TeamMessage;

import java.util.List;

/**
 * 读取当前 Agent 的文件 inbox。
 */
public class CheckInboxTool implements Tool {

    private final MessageBus bus;

    private final String agentName;

    public CheckInboxTool(MessageBus bus, String agentName) {
        this.bus = bus;
        this.agentName = agentName;
    }

    @Override
    /*
     * {
     *   "name": "check_inbox",
     *   "description": "Check this agent's inbox for teammate messages.",
     *   "input_schema": {"type": "object", "properties": {}}
     * }
     */
    public ToolDefinition getDefinition() {
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", new JSONObject());
        return new ToolDefinition("check_inbox",
                "Check this agent's inbox for teammate messages.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        List<TeamMessage> messages = bus.readInbox(agentName);
        if (messages.isEmpty()) {
            return new ToolResult("(inbox empty)");
        }
        return new ToolResult(bus.formatInbox(messages));
    }
}
