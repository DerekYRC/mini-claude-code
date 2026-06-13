package org.miniclaudecode.demo.s01;

import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.tool.BashTool;

import java.io.File;
import java.util.Collections;
import java.util.Scanner;

public class S01AgentLoopDemo {

	public static void main(String[] args) {
		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));

		org.miniclaudecode.core.AgentLoop loop = new org.miniclaudecode.core.AgentLoop(
				new AnthropicLlmClient(config),
				Collections.singletonList(new BashTool(new File("."))));

		Scanner scanner = new Scanner(System.in);
		System.out.print("You> ");
		AssistantMessage answer = loop.run(scanner.nextLine());
		for (ContentBlock block : answer.getContent()) {
			if (block instanceof TextBlock) {
				System.out.println("Claude> " + ((TextBlock) block).getText());
			}
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
