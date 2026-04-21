package me.luckyraven.file.configuration;

import me.luckyraven.core.autowire.bean.SettingsLookup;
import me.luckyraven.core.autowire.bean.conditional.ConditionalOnSetting;

import java.util.Map;

/**
 * {@link SettingsLookup} implementation backed by {@link Settings#getSettingsMap()}. Resolves dotted-path keys directly
 * against the loaded YAML map. Unknown or non-boolean values fail closed (return {@code false}) so a misspelled
 * {@link ConditionalOnSetting} skips the bean instead of accidentally enabling it.
 */
public class SettingsLookupImpl implements SettingsLookup {

	@Override
	public boolean isEnabled(String key) {
		Map<String, Object> map   = Settings.getSettingsMap();
		Object              value = map.get(key);

		if (value instanceof Boolean bool) {
			return bool;
		}

		if (value instanceof String text) {
			return Boolean.parseBoolean(text);
		}

		return false;
	}
}
