package org.miniclaudecode.demo.s01;

import org.miniclaudecode.core.AgentLoop;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * s01 启动入口：注册一个 bash 工具，然后把用户输入不断交给 AgentLoop。
 */
public class S01AgentLoopDemo {

	// system prompt 放在 demo 顶部，便于对照本章角色和工具边界。
	private static final String SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". Use bash to solve tasks. Act, don't explain.";

	public static void main(String[] args) {
		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));
		config.setSystemPrompt(SYSTEM_PROMPT);
		AnthropicLlmClient llmClient = new AnthropicLlmClient(config);

		// s01 只注册一个工具，聚焦最小工具闭环。
		List<Tool> tools = Collections.singletonList(new BashTool(new File(".")));
		AgentLoopListener loopListener = new AgentLoopListener() {
			@Override
			public void beforeToolUse(ToolUseBlock toolUse) {
				System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
			}

			@Override
			public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
				System.out.println("ToolResult> " + result.getContent());
			}
		};
		AgentLoop loop = new AgentLoop(
				llmClient,
				tools,
				loopListener);

		System.out.println("s01: Agent Loop");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s01 >> ");
			if (!scanner.hasNextLine()) {
				break;
			}

			String query = scanner.nextLine();
			if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
					|| "exit".equalsIgnoreCase(query.trim())) {
				break;
			}

			// history 留在 demo 外层，支持用户连续输入时保留上下文。
			history.add(Message.user(query));
			AssistantMessage answer = loop.run(history);
			for (ContentBlock block : answer.getContent()) {
				if (block instanceof TextBlock) {
					System.out.println(((TextBlock) block).getText());
				}
			}
			System.out.println();
		}
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing env: " + name);
		}
		return value;
	}
}
