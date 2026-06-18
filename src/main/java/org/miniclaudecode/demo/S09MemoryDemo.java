package org.miniclaudecode.demo;

import org.miniclaudecode.compact.CompactionPipeline;
import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.CompactingAgentLoop;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.MemoryAgentLoop;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.memory.MemoryConsolidator;
import org.miniclaudecode.memory.MemoryExtractor;
import org.miniclaudecode.memory.MemoryManager;
import org.miniclaudecode.memory.MemorySelector;
import org.miniclaudecode.memory.MemoryStore;
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
 * s09 启动入口：用文件系统保存记忆，并在每轮开始按需加载相关正文。
 */
public class S09MemoryDemo {

	private static final String SYSTEM_PROMPT_TEMPLATE = "You are a coding agent at "
			+ System.getProperty("user.dir")
			+ ".%s\n"
			+ "Relevant memories are injected below. Respect user preferences from memory.\n"
			+ "When the user says 'remember' or expresses a clear preference, extract it as a memory.";

	private static final String SUBAGENT_SYSTEM_PROMPT = "You are a coding agent at "
			+ System.getProperty("user.dir")
			+ ". Complete the task you were given, then return a concise summary. Do not delegate further.";

	public static void main(String[] args) {
		File workdir = new File(".");

		AnthropicConfig config = config("");
		AnthropicLlmClient client = new AnthropicLlmClient(config);

		AnthropicConfig subagentConfig = config(SUBAGENT_SYSTEM_PROMPT);
		AnthropicLlmClient subagentClient = new AnthropicLlmClient(subagentConfig);

		ToolRegistry subagentTools = basicTools(workdir);
		ToolRegistry registry = basicTools(workdir)
				.register(new TaskTool(subagentClient, subagentTools));

		MemoryStore store = new MemoryStore(workdir);
		MemoryManager memoryManager = new MemoryManager(store,
				new MemorySelector(client),
				new MemoryExtractor(client),
				new MemoryConsolidator(client));

		CompactionPipeline pipeline = new CompactionPipeline(workdir, client);
		CompactingAgentLoop compactingLoop = new CompactingAgentLoop(client, registry, pipeline,
				new AgentLoopListener() {
					@Override
					public void beforeToolUse(ToolUseBlock toolUse) {
						System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
					}

					@Override
					public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
						System.out.println("ToolResult> " + preview(result.getContent()));
					}
				});
		MemoryAgentLoop loop = new MemoryAgentLoop(compactingLoop, memoryManager);

		System.out.println("s09: Memory");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s09 >> ");
			if (!scanner.hasNextLine()) {
				break;
			}
			String query = scanner.nextLine();
			if (query == null || query.isBlank() || "q".equalsIgnoreCase(query.trim())
					|| "exit".equalsIgnoreCase(query.trim())) {
				break;
			}

			// MEMORY.md 是便宜索引，每轮都放进 system prompt；正文只在相关时注入用户消息。
			config.setSystemPrompt(systemPrompt(memoryManager.systemMemoryIndex()));
			history.add(memoryManager.injectRelevantMemories(history, query));
			List<Message> preCompactSnapshot = loop.snapshot(history);

			AssistantMessage answer = loop.run(history, preCompactSnapshot);
			for (ContentBlock block : answer.getContent()) {
				if (block instanceof TextBlock) {
					System.out.println(((TextBlock) block).getText());
				}
			}
			System.out.println();
		}
	}

	private static ToolRegistry basicTools(File workdir) {
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

	private static String systemPrompt(String memoryIndex) {
		String memoriesSection = "";
		if (memoryIndex != null && !memoryIndex.isBlank() && !"(no memories yet)".equals(memoryIndex)) {
			memoriesSection = "\n\nMemories available:\n" + memoryIndex;
		}
		return String.format(SYSTEM_PROMPT_TEMPLATE, memoriesSection);
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
