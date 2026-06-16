package org.miniclaudecode.permission;

import org.miniclaudecode.core.ToolUseBlock;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 工具执行前的权限边界。
 *
 * s03 把“能不能做、要不要问用户”放在工具真正执行之前，
 * 这样模型即使请求了危险操作，Java 侧也可以拒绝或暂停确认。
 */
public class PermissionManager {

	private final File workdir;

	private final ApprovalPrompter approvalPrompter;

	private final List<String> denyList = Arrays.asList("rm -rf /", "sudo", "shutdown", "reboot", "mkfs", "dd if=",
			"> /dev/sda");

	private final List<String> askPatterns = Arrays.asList("rm ", "> /etc/", "chmod 777");

	public PermissionManager(File workdir, ApprovalPrompter approvalPrompter) {
		this.workdir = workdir;
		this.approvalPrompter = approvalPrompter;
	}

	public PermissionDecision check(ToolUseBlock toolUse) {
		if ("bash".equals(toolUse.getName())) {
			return checkBash(toolUse);
		}
		if ("write_file".equals(toolUse.getName()) || "edit_file".equals(toolUse.getName())) {
			return checkFileWrite(toolUse);
		}
		return PermissionDecision.allow();
	}

	private PermissionDecision checkBash(ToolUseBlock toolUse) {
		String command = toolUse.getInput() == null ? "" : toolUse.getInput().getString("command");
		if (command == null) {
			command = "";
		}
		for (String pattern : denyList) {
			if (command.contains(pattern)) {
				// 硬阻止列表不询问用户，直接拒绝。
				return PermissionDecision.deny("Permission denied: '" + pattern + "' is on the deny list");
			}
		}
		for (String pattern : askPatterns) {
			if (command.contains(pattern)) {
				// 潜在破坏操作交给 ApprovalPrompter，由用户决定是否继续。
				return ask(toolUse, "Potentially destructive command");
			}
		}
		return PermissionDecision.allow();
	}

	private PermissionDecision checkFileWrite(ToolUseBlock toolUse) {
		String path = toolUse.getInput() == null ? "" : toolUse.getInput().getString("path");
		if (path == null || path.isBlank()) {
			return PermissionDecision.allow();
		}
		try {
			File target = new File(workdir, path).getCanonicalFile();
			File root = workdir.getCanonicalFile();
			if (!target.toPath().startsWith(root.toPath())) {
				// 文件写入类工具不能越过工作目录边界。
				return PermissionDecision.deny("Permission denied: path escapes workspace");
			}
			return PermissionDecision.allow();
		}
		catch (IOException e) {
			return PermissionDecision.deny("Permission denied: " + e.getMessage());
		}
	}

	private PermissionDecision ask(ToolUseBlock toolUse, String reason) {
		if (approvalPrompter.approve(toolUse, reason)) {
			return PermissionDecision.allow();
		}
		return PermissionDecision.deny("Permission denied by user: " + reason);
	}
}
