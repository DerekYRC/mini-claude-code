package org.miniclaudecode.hook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		for (Hook hook : eventHooks) {
			HookDecision decision = hook.run(context);
			if (decision != null && decision.isBlocked()) {
				return decision;
			}
		}
		return HookDecision.pass();
	}
}
