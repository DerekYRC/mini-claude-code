package org.miniclaudecode.demo.s04;

import org.miniclaudecode.core.AgentLoop;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolResultBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.hook.HookContext;
import org.miniclaudecode.hook.HookDecision;
import org.miniclaudecode.hook.HookEvent;
import org.miniclaudecode.hook.HookManager;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.EditFileTool;
import org.miniclaudecode.tool.GlobTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * s04 启动入口：把权限、日志、输出检查和停止统计都注册成 hook。
 */
public class S04HooksDemo {

	// system prompt 放在 demo 顶部，便于对照本章 hook 扩展点。
	private static final String SYSTEM_PROMPT = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". Use tools to solve tasks. Act, don't explain.";

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
		HookManager hookManager = hooks(workdir, scanner);
		AgentLoop loop = new AgentLoop(new AnthropicLlmClient(config), registry, new org.miniclaudecode.core.AgentLoopListener() {
		}, hookManager);

		System.out.println("s04: Hooks");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		while (true) {
			System.out.print("s04 >> ");
			if (!scanner.hasNextLine()) {
				break;
			}

			String query = scanner.nextLine();
			if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
					|| "exit".equalsIgnoreCase(query.trim())) {
				break;
			}

			triggerUserPromptHook(hookManager, query);
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

	private static HookManager hooks(File workdir, Scanner scanner) {
		HookManager hooks = new HookManager();
		// hook 写在入口类里，保持扩展点和触发位置相邻。
		hooks.register(HookEvent.USER_PROMPT_SUBMIT, context -> {
			System.out.println("[HOOK] UserPromptSubmit: working in " + workdir.getAbsolutePath());
			return HookDecision.pass();
		});
		hooks.register(HookEvent.PRE_TOOL_USE, context -> permissionHook(context, scanner));
		hooks.register(HookEvent.PRE_TOOL_USE, context -> {
			ToolUseBlock toolUse = context.getToolUse();
			System.out.println("[HOOK] PreToolUse: " + toolUse.getName() + " " + toolUse.getInput());
			return HookDecision.pass();
		});
		hooks.register(HookEvent.POST_TOOL_USE, context -> {
			String content = context.getToolResult() == null ? "" : context.getToolResult().getContent();
			if (content != null && content.length() > 100000) {
				System.out.println("[HOOK] PostToolUse: large output from " + context.getToolUse().getName());
			}
			return HookDecision.pass();
		});
		hooks.register(HookEvent.STOP, context -> {
			System.out.println("[HOOK] Stop: session used " + toolResultCount(context.getMessages()) + " tool calls");
			return HookDecision.pass();
		});
		return hooks;
	}

	private static HookDecision permissionHook(HookContext context, Scanner scanner) {
		ToolUseBlock toolUse = context.getToolUse();
		if (!"bash".equals(toolUse.getName())) {
			return HookDecision.pass();
		}
		String command = toolUse.getInput() == null ? "" : toolUse.getInput().getString("command");
		if (command == null) {
			command = "";
		}
		for (String pattern : Arrays.asList("rm -rf /", "sudo", "shutdown", "reboot", "mkfs", "dd if=")) {
			if (command.contains(pattern)) {
				System.out.println("[HOOK] Permission blocked: " + pattern);
				return HookDecision.block("Permission denied by hook: " + pattern);
			}
		}
		for (String pattern : Arrays.asList("rm ", "> /etc/", "chmod 777")) {
			if (command.contains(pattern)) {
				System.out.println("[HOOK] Permission asks: Potentially destructive command");
				System.out.print("Allow? [y/N] ");
				if (!scanner.hasNextLine()) {
					return HookDecision.block("Permission denied by hook");
				}
				String choice = scanner.nextLine().trim().toLowerCase();
				if (!"y".equals(choice) && !"yes".equals(choice)) {
					return HookDecision.block("Permission denied by hook");
				}
			}
		}
		return HookDecision.pass();
	}

	private static void triggerUserPromptHook(HookManager hookManager, String query) {
		// UserPromptSubmit 不在 AgentLoop 内触发，因为本章让 demo 负责接收用户输入。
		HookContext context = new HookContext(HookEvent.USER_PROMPT_SUBMIT);
		context.setUserPrompt(query);
		hookManager.trigger(HookEvent.USER_PROMPT_SUBMIT, context);
	}

	private static int toolResultCount(List<Message> messages) {
		int count = 0;
		for (Message message : messages) {
			for (ContentBlock block : message.getContent()) {
				if (block instanceof ToolResultBlock) {
					count++;
				}
			}
		}
		return count;
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing env: " + name);
		}
		return value;
	}
}
