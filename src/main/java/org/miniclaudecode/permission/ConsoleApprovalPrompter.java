package org.miniclaudecode.permission;

import org.miniclaudecode.core.ToolUseBlock;

import java.util.Scanner;

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
