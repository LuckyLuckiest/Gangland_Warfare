package org.luckyraven.gangland.core.bean;

import org.luckyraven.gangland.core.bean.conditional.ConditionalOnSetting;

/**
 * Contract used by the bean framework to evaluate {@link ConditionalOnSetting}. Implementations resolve a dotted-path
 * key against the project's settings file. Lives in {@code gangland-core} (per the project rule that feature modules
 * must not import {@code Settings} directly); the concrete implementation is wired from {@code gangland-impl}.
 */
public interface SettingsLookup {

	/**
	 * Resolve a dotted-path setting to a boolean. Unknown keys MUST return {@code false} so that a misspelled condition
	 * fails closed and the bean is skipped.
	 *
	 * @param key dotted path, e.g. {@code "gang.enabled"}
	 *
	 * @return {@code true} only if the key exists and resolves to a truthy value
	 */
	boolean isEnabled(String key);
}
