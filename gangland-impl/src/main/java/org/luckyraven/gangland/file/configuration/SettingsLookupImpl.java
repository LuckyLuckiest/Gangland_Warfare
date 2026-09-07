package org.luckyraven.gangland.file.configuration;

import org.luckyraven.keystone.bean.SettingsLookup;
import org.luckyraven.keystone.bean.conditional.ConditionalOnSetting;

import java.util.Map;

/**
 * {@link SettingsLookup} implementation backed by {@link Settings#getSettingsMap()}. Resolves dotted-path keys directly
 * against the loaded YAML map. Unknown or non-boolean values fail closed (return {@code false}) so a misspelled
 * {@link ConditionalOnSetting} skips the bean instead of accidentally enabling it.
 *
 * <p>CM-03: the map is keyed by Java <em>field</em> name ({@code gangEnabled}, see
 * {@code Settings.addEachFieldReflection()}), but the listener side resolves conditions reflectively as
 * <em>getter</em> names, so annotations across the codebase are written as {@code condition = "isGangEnabled"}.
 * A direct miss therefore falls back to the JavaBeans property name derived from the getter
 * ({@code isGangEnabled} / {@code getGangEnabled} &rarr; {@code gangEnabled}); without it
 * {@code @CommandHandler(condition = "isGangEnabled")} could never match and {@code GangCommand} never registered.
 */
public class SettingsLookupImpl implements SettingsLookup {

	@Override
	public boolean isEnabled(String key) {
		Map<String, Object> map = Settings.getSettingsMap();

		if (map.containsKey(key)) {
			return toBoolean(map.get(key));
		}

		String property = propertyName(key);

		if (property != null && map.containsKey(property)) {
			return toBoolean(map.get(property));
		}

		return false;
	}

	/**
	 * Strips a JavaBeans {@code is}/{@code get} prefix and decapitalizes the remainder, or returns {@code null} when
	 * {@code key} is not shaped like a getter name.
	 */
	private static String propertyName(String key) {
		String remainder = null;

		if (key.length() > 2 && key.startsWith("is")) {
			remainder = key.substring(2);
		} else if (key.length() > 3 && key.startsWith("get")) {
			remainder = key.substring(3);
		}

		if (remainder == null || !Character.isUpperCase(remainder.charAt(0))) {
			return null;
		}

		return Character.toLowerCase(remainder.charAt(0)) + remainder.substring(1);
	}

	private static boolean toBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}

		if (value instanceof String text) {
			return Boolean.parseBoolean(text);
		}

		return false;
	}
}
