package org.miniclaudecode.permission;

/**
 * 权限管线给 AgentLoop 的判断结果。
 *
 * 主循环只关心是否允许执行，以及拒绝时要回传给模型的说明。
 */
public class PermissionDecision {

	private boolean allowed;

	private String message;

	private PermissionDecision(boolean allowed, String message) {
		this.allowed = allowed;
		this.message = message;
	}

	public static PermissionDecision allow() {
		return new PermissionDecision(true, "Allowed");
	}

	public static PermissionDecision deny(String message) {
		return new PermissionDecision(false, message);
	}

	public boolean isAllowed() {
		return allowed;
	}

	public void setAllowed(boolean allowed) {
		this.allowed = allowed;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
