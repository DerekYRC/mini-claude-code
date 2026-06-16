package org.miniclaudecode.hook;

public class HookEvent {

	public static final String USER_PROMPT_SUBMIT = "UserPromptSubmit"; // 用户输入提交后

	public static final String PRE_TOOL_USE = "PreToolUse"; // 工具执行前

	public static final String POST_TOOL_USE = "PostToolUse"; // 工具执行后

	public static final String STOP = "Stop"; // Agent 循环停止时

	private HookEvent() {
	}
}
