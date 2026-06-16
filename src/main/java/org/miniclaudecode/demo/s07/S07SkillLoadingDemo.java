package org.miniclaudecode.demo.s07;

import org.miniclaudecode.core.AgentLoop;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.skill.SkillRegistry;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.EditFileTool;
import org.miniclaudecode.tool.GlobTool;
import org.miniclaudecode.tool.LoadSkillTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.ToolRegistry;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * s07 启动入口：system prompt 只放技能目录，正文通过 load_skill 按需加载。
 */
public class S07SkillLoadingDemo {

	// system prompt 模板只预留技能目录，完整技能正文由 load_skill 按需返回。
	private static final String SYSTEM_PROMPT_TEMPLATE = "You are a coding agent at " + System.getProperty("user.dir")
			+ ". Skills available:\n%s\nUse load_skill to get full details when needed.";

	public static void main(String[] args) {
		File workdir = new File(".");
		SkillRegistry skillRegistry = new SkillRegistry(new File(workdir, "skills"));

		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));
		config.setSystemPrompt(String.format(SYSTEM_PROMPT_TEMPLATE, skillRegistry.getDescriptions()));

		// 本章不注册 task 工具，只保留技能目录和按需加载正文。
		ToolRegistry registry = new ToolRegistry()
				.register(new BashTool(workdir))
				.register(new ReadFileTool(workdir))
				.register(new WriteFileTool(workdir))
				.register(new EditFileTool(workdir))
				.register(new GlobTool(workdir))
				.register(new LoadSkillTool(skillRegistry));

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

		System.out.println("s07: Skill Loading");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s07 >> ");
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
