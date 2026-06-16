package org.miniclaudecode.demo.s03;

import org.miniclaudecode.core.AgentLoop;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.permission.ConsoleApprovalPrompter;
import org.miniclaudecode.permission.PermissionManager;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.EditFileTool;
import org.miniclaudecode.tool.GlobTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s03 启动入口：复用 s02 的工具池，并在工具执行前挂上权限管线。
 */
public class S03PermissionDemo {

	// 我在提示词里提醒模型高风险操作要审批；真正的阻止和询问仍由 PermissionManager 执行。
	private static final String SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". All destructive operations require user approval.";

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
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
				.register(new GlobTool(workdir));
		// s03 仍复用 s02 工具池，只是在执行前多一道“能不能做”的门。
		PermissionManager permissionManager = new PermissionManager(workdir, new ConsoleApprovalPrompter(scanner));
		AgentLoop loop = new AgentLoop(new AnthropicLlmClient(config), registry, new AgentLoopListener() {
			@Override
			public void beforeToolUse(ToolUseBlock toolUse) {
				System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
			}

			@Override
			public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
				System.out.println("ToolResult> " + preview(result.getContent()));
			}
		}, permissionManager);

		System.out.println("s03: Permission");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		while (true) {
			System.out.print("s03 >> ");
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
		if (content == null || content.length() <= 500) {
			return content;
		}
		return content.substring(0, 500) + "\n... (" + (content.length() - 500) + " more chars)";
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing env: " + name);
		}
		return value;
	}
}
