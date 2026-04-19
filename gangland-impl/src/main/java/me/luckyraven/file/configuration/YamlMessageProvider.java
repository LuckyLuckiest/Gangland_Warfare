package me.luckyraven.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * {@link MessageProvider} backed by a primary {@link YamlConfiguration} (the admin's on-disk file) and an optional
 * fallback (the pristine jar resource). A key missing from the primary falls through to the fallback, so admin typos
 * or accidental deletions degrade to the default string instead of surfacing as {@code null} at call sites.
 * Constructed by {@link me.luckyraven.file.LanguageLoader} after the per-language {@code message_<lang>.yml} is
 * loaded.
 */
public class YamlMessageProvider implements MessageProvider {

	private final YamlConfiguration primary;
	private final YamlConfiguration fallback;

	public YamlMessageProvider(YamlConfiguration primary, YamlConfiguration fallback) {
		this.primary  = primary;
		this.fallback = fallback;
	}

	public YamlMessageProvider(YamlConfiguration primary) {
		this(primary, null);
	}

	@Override
	public String getString(String path) {
		String value = primary.getString(path);
		if (value != null) return value;
		return fallback != null ? fallback.getString(path) : null;
	}

	@Override
	public List<String> getStringList(String path) {
		if (primary.contains(path)) return primary.getStringList(path);
		return fallback != null ? fallback.getStringList(path) : List.of();
	}

}
