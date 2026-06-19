package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.protocol.ProtocolService;
import org.miniclaudecode.team.MessageBus;
import org.miniclaudecode.team.TeamMessage;

import java.util.List;

/**
 * Lead 专用 inbox 工具。
 *
 * 读取消息前先走协议路由，避免 response 被消费后 pending 状态没有更新。
 */
public class ProtocolCheckInboxTool implements Tool {

    private final ProtocolService protocol;

    private final MessageBus bus;

    public ProtocolCheckInboxTool(ProtocolService protocol, MessageBus bus) {
        this.protocol = protocol;
        this.bus = bus;
    }

    /*
     * {
     *   "name": "check_inbox",
     *   "description": "Check Lead's inbox. Routes protocol responses automatically.",
     *   "input_schema": {"type": "object", "properties": {}, "required": []}
     * }
     */
    @Override
    public ToolDefinition getDefinition() {
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", new JSONObject())
                .fluentPut("required", new JSONArray());
        return new ToolDefinition("check_inbox",
                "Check Lead's inbox. Routes protocol responses automatically.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        List<TeamMessage> messages = protocol.consumeLeadInbox();
        if (messages.isEmpty()) {
            return new ToolResult("(inbox empty)");
        }
        return new ToolResult(bus.formatInbox(messages));
    }
}
