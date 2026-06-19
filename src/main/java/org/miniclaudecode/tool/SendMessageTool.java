package org.miniclaudecode.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.team.MessageBus;

/**
 * 通过文件邮箱给另一个 Agent 发消息。
 */
public class SendMessageTool implements Tool {

    private final MessageBus bus;

    private final String fromAgent;

    public SendMessageTool(MessageBus bus, String fromAgent) {
        this.bus = bus;
        this.fromAgent = fromAgent;
    }

    @Override
    /*
     * {
     *   "name": "send_message",
     *   "description": "Send a message to another agent via MessageBus.",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {
     *       "to": {"type": "string"},
     *       "content": {"type": "string"}
     *     },
     *     "required": ["to", "content"]
     *   }
     * }
     */
    public ToolDefinition getDefinition() {
        JSONObject properties = new JSONObject()
                .fluentPut("to", new JSONObject().fluentPut("type", "string"))
                .fluentPut("content", new JSONObject().fluentPut("type", "string"));
        JSONObject schema = new JSONObject()
                .fluentPut("type", "object")
                .fluentPut("properties", properties)
                .fluentPut("required", new JSONArray().fluentAdd("to").fluentAdd("content"));
        return new ToolDefinition("send_message",
                "Send a message to another agent via MessageBus.", schema);
    }

    @Override
    public ToolResult execute(JSONObject input) {
        String to = input == null ? "" : input.getString("to");
        String content = input == null ? "" : input.getString("content");
        if (to == null || to.isBlank()) {
            return new ToolResult("Error: to is required");
        }
        if (content == null || content.isBlank()) {
            return new ToolResult("Error: content is required");
        }
        bus.send(fromAgent, to.trim(), content.trim());
        return new ToolResult("Sent to " + to.trim());
    }
}
