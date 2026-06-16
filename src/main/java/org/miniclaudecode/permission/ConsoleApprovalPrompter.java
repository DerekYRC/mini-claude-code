package org.miniclaudecode.permission;

import org.miniclaudecode.core.ToolUseBlock;

import java.util.Scanner;

/**
 * 教学版审批器：直接在控制台询问用户，只有 y/yes 才允许执行。
 */
public class ConsoleApprovalPrompter implements ApprovalPrompter {

	private final Scanner scanner;

	public ConsoleApprovalPrompter(Scanner scanner) {
		this.scanner = scanner;
	}

	@Override
	public boolean approve(ToolUseBlock toolUse, String reason) {
		System.out.println();
		System.out.println("Permission> " + reason);
		System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
		System.out.print("Allow? [y/N] ");
		if (!scanner.hasNextLine()) {
			return false;
		}
		String choice = scanner.nextLine().trim().toLowerCase();
		return "y".equals(choice) || "yes".equals(choice);
	}
}
