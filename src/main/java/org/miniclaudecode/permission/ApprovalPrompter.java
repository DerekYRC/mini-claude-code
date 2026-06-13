package org.miniclaudecode.permission;

import org.miniclaudecode.core.ToolUseBlock;

public interface ApprovalPrompter {

	boolean approve(ToolUseBlock toolUse, String reason);
}
