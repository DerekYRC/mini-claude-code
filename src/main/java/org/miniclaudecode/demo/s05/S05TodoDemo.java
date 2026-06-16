package org.miniclaudecode.demo.s05;

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
import org.miniclaudecode.tool.EditFileTool;
import org.miniclaudecode.tool.GlobTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.TodoWriteTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s05 启动入口：把“先计划再执行”的约束交给 todo_write 工具和 system prompt。
 */
public class S05TodoDemo {

	// system prompt 放在 demo 顶部，便于对照 todo_write 的计划约束。
	private static final String SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". Before starting any multi-step task, use todo_write to plan your steps. "
			+ "Keep exactly one task in_progress while working. Mark tasks completed as you finish. "
			+ "Act, don't explain.";

	public static void main(String[] args) {
		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));
		config.setSystemPrompt(SYSTEM_PROMPT);

		File workdir = new File(".");
		ToolRegistry registry = new ToolRegistry()
				.register(new BashTool(workdir))
				.register(new ReadFileTool(workdir))
				.register(new WriteFileTool(workdir))
				.register(new EditFileTool(workdir))
				.register(new GlobTool(workdir))
				.register(new TodoWriteTool());
		AgentLoop loop = new AgentLoop(new AnthropicLlmClient(config), registry, new AgentLoopListener() {
			@Override
			public void beforeToolUse(ToolUseBlock toolUse) {
				System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
			}

			@Override
			public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
				System.out.println("ToolResult> " + preview(result.getContent()));
			}
		});

		System.out.println("s05: Todo");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s05 >> ");
			if (!scanner.hasNextLine()) {
				break;
			}

			String query = scanner.nextLine();
			if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
					|| "exit".equalsIgnoreCase(query.trim())) {
				break;
			}

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

	private static String preview(String content) {
		if (content == null || content.length() <= 800) {
			return content;
		}
		return content.substring(0, 800) + "\n... (" + (content.length() - 800) + " more chars)";
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing env: " + name);
		}
		return value;
	}
}
