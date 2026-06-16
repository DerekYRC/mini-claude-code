package org.miniclaudecode.hook;

/**
 * Hook 的返回值。
 *
 * 只有 PRE_TOOL_USE 阶段的 block 会阻止工具执行，其他阶段主要用于观察和记录。
 */
public class HookDecision {

	private boolean blocked;

	private String message;

	private HookDecision(boolean blocked, String message) {
		this.blocked = blocked;
		this.message = message;
	}

	public static HookDecision pass() {
		return new HookDecision(false, null);
	}

	public static HookDecision block(String message) {
		return new HookDecision(true, message);
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
