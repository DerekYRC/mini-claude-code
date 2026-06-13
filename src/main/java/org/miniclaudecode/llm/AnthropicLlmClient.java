package org.miniclaudecode.llm;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ThinkingBlock;
import org.miniclaudecode.core.ToolResultBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.core.UnknownBlock;
import org.miniclaudecode.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnthropicLlmClient implements LlmClient {

	private final AnthropicConfig config;

	public AnthropicLlmClient(AnthropicConfig config) {
		this.config = config;
	}

	@Override
	public AssistantMessage chat(List<Message> messages, List<ToolDefinition> tools) {
		String body = JSON.toJSONString(toRequestJson(messages, tools));
		HttpRequest request = HttpRequest.post(messagesUrl()).timeout(config.getTimeoutMillis()).body(body);
		for (Map.Entry<String, String> header : requestHeaders().entrySet()) {
			request.header(header.getKey(), header.getValue());
		}
		HttpResponse response = request.execute();

		if (response.getStatus() < 200 || response.getStatus() >= 300) {
			throw new IllegalStateException("LLM request failed: HTTP " + response.getStatus() + "\n" + response.body());
		}
		return parseResponse(response.body());
	}

	public Map<String, String> requestHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("x-api-key", config.getApiKey());
		headers.put("anthropic-version", "2023-06-01");
		headers.put("content-type", "application/json");
		return headers;
	}

	public AssistantMessage parseResponse(String body) {
		JSONObject json = JSON.parseObject(body);
		JSONArray content = json.getJSONArray("content");
		List<ContentBlock> blocks = new ArrayList<>();
		if (content != null) {
			for (int i = 0; i < content.size(); i++) {
				JSONObject block = content.getJSONObject(i);
				String type = block.getString("type");
				if ("text".equals(type)) {
					blocks.add(new TextBlock(valueOrEmpty(block, "text")));
				}
				else if ("thinking".equals(type)) {
					blocks.add(new ThinkingBlock(valueOrEmpty(block, "thinking"), block.getString("signature")));
				}
				else if ("tool_use".equals(type)) {
					JSONObject input = block.getJSONObject("input");
					blocks.add(new ToolUseBlock(block.getString("id"), block.getString("name"),
							input == null ? new JSONObject() : input));
				}
				else {
					blocks.add(new UnknownBlock(type, block));
				}
			}
		}
		String stopReason = json.getString("stop_reason");
		return new AssistantMessage(blocks, stopReason == null ? "end_turn" : stopReason);
	}

	public JSONObject toRequestJson(List<Message> messages, List<ToolDefinition> tools) {
		JSONObject request = new JSONObject();
		request.put("model", config.getModel());
		request.put("max_tokens", config.getMaxTokens());
		if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
			request.put("system", config.getSystemPrompt());
		}
		request.put("messages", toMessagesJson(messages));
		if (tools != null && !tools.isEmpty()) {
			request.put("tools", toToolsJson(tools));
		}
		return request;
	}

	private JSONArray toMessagesJson(List<Message> messages) {
		JSONArray array = new JSONArray();
		for (Message message : messages) {
			JSONObject item = new JSONObject();
			item.put("role", message.getRole());
			item.put("content", toContentJson(message.getContent()));
			array.add(item);
		}
		return array;
	}

	private JSONArray toContentJson(List<ContentBlock> content) {
		JSONArray array = new JSONArray();
		for (ContentBlock block : content) {
			if (block instanceof TextBlock) {
				TextBlock textBlock = (TextBlock) block;
				array.add(new JSONObject()
						.fluentPut("type", "text")
						.fluentPut("text", textBlock.getText()));
			}
			else if (block instanceof ThinkingBlock) {
				ThinkingBlock thinkingBlock = (ThinkingBlock) block;
				JSONObject json = new JSONObject()
						.fluentPut("type", "thinking")
						.fluentPut("thinking", thinkingBlock.getThinking());
				if (thinkingBlock.getSignature() != null && !thinkingBlock.getSignature().isBlank()) {
					json.put("signature", thinkingBlock.getSignature());
				}
				array.add(json);
			}
			else if (block instanceof ToolUseBlock) {
				ToolUseBlock toolUse = (ToolUseBlock) block;
				array.add(new JSONObject()
						.fluentPut("type", "tool_use")
						.fluentPut("id", toolUse.getId())
						.fluentPut("name", toolUse.getName())
						.fluentPut("input", toolUse.getInput()));
			}
			else if (block instanceof ToolResultBlock) {
				ToolResultBlock toolResult = (ToolResultBlock) block;
				array.add(new JSONObject()
						.fluentPut("type", "tool_result")
						.fluentPut("tool_use_id", toolResult.getToolUseId())
						.fluentPut("content", toolResult.getContent()));
			}
			else if (block instanceof UnknownBlock) {
				array.add(((UnknownBlock) block).getRaw());
			}
		}
		return array;
	}

	private JSONArray toToolsJson(List<ToolDefinition> tools) {
		JSONArray array = new JSONArray();
		for (ToolDefinition tool : tools) {
			array.add(new JSONObject()
					.fluentPut("name", tool.getName())
					.fluentPut("description", tool.getDescription())
					.fluentPut("input_schema", tool.getInputSchema()));
		}
		return array;
	}

	private String messagesUrl() {
		String baseUrl = config.getBaseUrl();
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl + "/v1/messages";
	}

	private String valueOrEmpty(JSONObject json, String key) {
		String value = json.getString(key);
		return value == null ? "" : value;
	}
}
