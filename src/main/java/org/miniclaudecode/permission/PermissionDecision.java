package org.miniclaudecode.permission;

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
