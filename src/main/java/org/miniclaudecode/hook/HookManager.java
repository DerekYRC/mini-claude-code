package org.miniclaudecode.hook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hook 调度器。
 *
 * s04 的关键思想是：主循环只在固定位置触发事件，
 * 具体扩展能力挂在 HookManager 上，不继续写进 AgentLoop。
 */
public class HookManager {

	private final Map<String, List<Hook>> hooks = new LinkedHashMap<>();

	public HookManager register(String event, Hook hook) {
		hooks.computeIfAbsent(event, key -> new ArrayList<>()).add(hook);
		return this;
	}

	public HookDecision trigger(String event, HookContext context) {
		List<Hook> eventHooks = hooks.get(event);
		if (eventHooks == null) {
			return HookDecision.pass();
		}
		// 同一个事件可以注册多个 hook，按注册顺序执行。
		for (Hook hook : eventHooks) {
			HookDecision decision = hook.run(context);
			if (decision != null && decision.isBlocked()) {
				return decision;
			}
		}
		return HookDecision.pass();
	}
}
