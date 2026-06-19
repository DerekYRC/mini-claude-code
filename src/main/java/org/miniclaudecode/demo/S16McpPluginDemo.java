package org.miniclaudecode.demo;

import org.miniclaudecode.core.AgentLoopListener;
import org.miniclaudecode.core.AssistantMessage;
import org.miniclaudecode.core.ContentBlock;
import org.miniclaudecode.core.DynamicMcpAgentLoop;
import org.miniclaudecode.core.Message;
import org.miniclaudecode.core.TextBlock;
import org.miniclaudecode.core.ToolUseBlock;
import org.miniclaudecode.llm.AnthropicConfig;
import org.miniclaudecode.llm.AnthropicLlmClient;
import org.miniclaudecode.mcp.McpManager;
import org.miniclaudecode.mcp.McpToolPool;
import org.miniclaudecode.tool.BashTool;
import org.miniclaudecode.tool.ReadFileTool;
import org.miniclaudecode.tool.Tool;
import org.miniclaudecode.tool.ToolResult;
import org.miniclaudecode.tool.WriteFileTool;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * s16 启动入口：把 mock MCP server 接进同一个工具池。
 */
public class S16McpPluginDemo {

	private static final String SYSTEM_PROMPT = "You are a coding agent. Act, don't explain.\n\n"
			+ "Available tools: bash, read_file, write_file, connect_mcp.\n"
			+ "MCP tools are prefixed mcp__{server}__{tool}.\n\n"
			+ "Working directory: " + System.getProperty("user.dir");

	public static void main(String[] args) {
		AnthropicConfig config = new AnthropicConfig();
		config.setBaseUrl(requiredEnv("ANTHROPIC_BASE_URL"));
		config.setApiKey(requiredEnv("ANTHROPIC_API_KEY"));
		config.setModel(requiredEnv("MODEL_ID"));
		config.setSystemPrompt(SYSTEM_PROMPT);

		File workdir = new File(".");
		List<Tool> builtinTools = Arrays.asList(
				new BashTool(workdir),
				new ReadFileTool(workdir),
				new WriteFileTool(workdir));

		McpManager mcpManager = new McpManager();
		McpToolPool toolPool = new McpToolPool(builtinTools, mcpManager);
		DynamicMcpAgentLoop loop = new DynamicMcpAgentLoop(new AnthropicLlmClient(config),
				toolPool, new AgentLoopListener() {
					@Override
					public void beforeToolUse(ToolUseBlock toolUse) {
						System.out.println("Tool> " + toolUse.getName()
								+ " " + toolUse.getInput());
					}

					@Override
					public void afterToolUse(ToolUseBlock toolUse, ToolResult result) {
						System.out.println("ToolResult> " + preview(result.getContent()));
					}
				});

		System.out.println("s16: MCP Plugin");
		System.out.println("输入问题，回车发送。输入 q 退出。\n");

		List<Message> history = new ArrayList<>();
		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.print("s16 >> ");
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
			printText(answer);
			System.out.println();
		}
	}

	private static void printText(AssistantMessage answer) {
		if (answer == null || answer.getContent() == null) {
			return;
		}
		for (ContentBlock block : answer.getContent()) {
			if (block instanceof TextBlock) {
				System.out.println(((TextBlock) block).getText());
			}
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
