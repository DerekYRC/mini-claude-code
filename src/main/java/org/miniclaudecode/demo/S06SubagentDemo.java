package org.miniclaudecode.demo;

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
import org.miniclaudecode.tool.TaskTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s06 启动入口：父 Agent 拥有 task 工具，子 Agent 只拥有基础工具。
 */
public class S06SubagentDemo {

	// 父 Agent 可以使用 task 工具把复杂子问题委托出去。
	private static final String PARENT_SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". For complex sub-problems, use the task tool to spawn a subagent.";

	// 子 Agent 不注册 task 工具，提示词也明确禁止继续委托。
	private static final String SUBAGENT_SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". Complete the task you were given, then return a concise summary. Do not delegate further.";

	public static void main(String[] args) {
		File workdir = new File(".");

		AnthropicConfig parentConfig = config(PARENT_SYSTEM_PROMPT);
		AnthropicConfig subConfig = config(SUBAGENT_SYSTEM_PROMPT);

		// 子工具池不包含 task，避免子 Agent 递归创建更多子 Agent。
		ToolRegistry subTools = baseTools(workdir);
		// 父工具池包含 task，用来把复杂子任务委托给干净上下文的子 Agent。
		ToolRegistry parentTools = baseTools(workdir)
				.register(new TaskTool(new AnthropicLlmClient(subConfig), subTools));

		AgentLoop loop = new AgentLoop(new AnthropicLlmClient(parentConfig), parentTools, new AgentLoopListener() {
			@Override
			public void beforeToolUse(ToolUseBlock toolUse) {
				System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
			}

			@Override
			public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
				System.out.println("ToolResult> " + preview(result.getContent()));
			}
		});

		System.out.println("s06: Subagent");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s06 >> ");
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

	private static ToolRegistry baseTools(File workdir) {
		return new ToolRegistry()
				.register(new BashTool(workdir))
				.register(new ReadFileTool(workdir))
				.register(new WriteFileTool(workdir))
				.register(new EditFileTool(workdir))
				.register(new GlobTool(workdir));
	}

	private static AnthropicConfig config(String systemPrompt) {
		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));
		config.setSystemPrompt(systemPrompt);
		return config;
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
