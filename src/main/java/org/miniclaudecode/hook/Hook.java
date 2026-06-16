package org.miniclaudecode.hook;

/**
 * 一个挂在 AgentLoop 事件点上的扩展函数。
 */
public interface Hook {

	HookDecision run(HookContext context);
}
