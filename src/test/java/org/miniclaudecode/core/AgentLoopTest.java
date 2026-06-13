package org.miniclaudecode.core;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.miniclaudecode.llm.LlmClient;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolDefinition;
import org.miniclaudecode.tool.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentLoopTest {

	@Test
	public void runExecutesToolUseAndSendsToolResultBackToLlm() {
		FakeLlmClient llm = new FakeLlmClient();
		AgentLoop loop = new AgentLoop(llm, Collections.singletonList(new EchoTool()));

		AssistantMessage answer = loop.run("where am I?");

		assertThat(((TextBlock) answer.getContent().get(0)).getText()).isEqualTo("done");
		assertThat(llm.calls).hasSize(2);

		List<Message> secondCallMessages = llm.calls.get(1);
		assertThat(secondCallMessages).hasSize(3);
		assertThat(secondCallMessages.get(0).getRole()).isEqualTo("user");
		assertThat(secondCallMessages.get(1).getRole()).isEqualTo("assistant");
		assertThat(secondCallMessages.get(2).getRole()).isEqualTo("user");

		ToolResultBlock resultBlock = (ToolResultBlock) secondCallMessages.get(2).getContent().get(0);
		assertThat(resultBlock.getToolUseId()).isEqualTo("toolu_1");
		assertThat(resultBlock.getContent()).isEqualTo("ran pwd");
	}

	private static class FakeLlmClient implements LlmClient {

		private final List<List<Message>> calls = new ArrayList<>();

		@Override
		public AssistantMessage chat(List<Message> messages, List<ToolDefinition> tools) {
			calls.add(new ArrayList<>(messages));
			if (calls.size() == 1) {
				JSONObject input = new JSONObject();
				input.put("command", "pwd");
				return new AssistantMessage(Collections.singletonList(
						new ToolUseBlock("toolu_1", "bash", input)), "tool_use");
			}
			return new AssistantMessage(Collections.singletonList(new TextBlock("done")), "end_turn");
		}
	}

	private static class EchoTool implements Tool {

		@Override
		public ToolDefinition getDefinition() {
			return new ToolDefinition("bash", "run shell command", new JSONObject());
		}

		@Override
		public ToolResult execute(JSONObject input) {
			return new ToolResult("ran " + input.getString("command"));
		}
	}
}
