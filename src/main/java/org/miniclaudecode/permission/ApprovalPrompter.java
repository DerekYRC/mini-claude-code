package org.miniclaudecode.permission;

import org.miniclaudecode.core.ToolUseBlock;

/**
 * 把“如何询问用户”从权限规则中拆出来，方便替换成控制台、GUI 或自动审批。
 */
public interface ApprovalPrompter {

	boolean approve(ToolUseBlock toolUse, String reason);
}
