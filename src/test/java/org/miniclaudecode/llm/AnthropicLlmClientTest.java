package org.miniclaudecode.llm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ThinkingBlock;
import org.miniclaudecode.core.ToolUseBlock;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class AnthropicLlmClientTest {

	@Test
	public void parseResponseKeepsThinkingTextAndToolUseBlocks() {
		String json = "{"
				+ "\"stop_reason\":\"tool_use\","
				+ "\"content\":["
				+ "{\"type\":\"thinking\",\"thinking\":\"internal\",\"signature\":\"sig_1\"},"
				+ "{\"type\":\"text\",\"text\":\"I will run pwd\"},"
				+ "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"bash\",\"input\":{\"command\":\"pwd\"}}"
				+ "]"
				+ "}";

		AssistantMessage message = new AnthropicLlmClient(new AnthropicConfig()).parseResponse(json);

		assertThat(message.getStopReason()).isEqualTo("tool_use");
		assertThat(message.getContent()).hasSize(3);

		ThinkingBlock thinking = (ThinkingBlock) message.getContent().get(0);
		assertThat(thinking.getThinking()).isEqualTo("internal");
		assertThat(thinking.getSignature()).isEqualTo("sig_1");

		TextBlock text = (TextBlock) message.getContent().get(1);
		assertThat(text.getText()).isEqualTo("I will run pwd");

		ToolUseBlock toolUse = (ToolUseBlock) message.getContent().get(2);
		assertThat(toolUse.getId()).isEqualTo("toolu_1");
		assertThat(toolUse.getName()).isEqualTo("bash");
		assertThat(toolUse.getInput().getString("command")).isEqualTo("pwd");
	}

	@Test
	public void toRequestJsonSerializesThinkingBlockBackIntoAssistantHistory() {
		AnthropicConfig config = new AnthropicConfig();
		config.setModel("test-model");
		AnthropicLlmClient client = new AnthropicLlmClient(config);
		Message assistant = Message.assistant(Arrays.asList(
				new ThinkingBlock("internal", "sig_1"),
				new TextBlock("answer")));

		JSONObject request = client.toRequestJson(Collections.singletonList(assistant), Collections.emptyList());

		assertThat(request.getString("model")).isEqualTo("test-model");
		JSONArray content = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
		assertThat(content.getJSONObject(0).getString("type")).isEqualTo("thinking");
		assertThat(content.getJSONObject(0).getString("thinking")).isEqualTo("internal");
		assertThat(content.getJSONObject(0).getString("signature")).isEqualTo("sig_1");
		assertThat(content.getJSONObject(1).getString("type")).isEqualTo("text");
	}
}
