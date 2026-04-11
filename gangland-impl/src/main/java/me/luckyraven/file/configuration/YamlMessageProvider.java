package me.luckyraven.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * {@link MessageProvider} backed by a single Bukkit {@link YamlConfiguration}. Constructed by
 * {@link me.luckyraven.file.LanguageLoader} after the per-language {@code message_<lang>.yml} file is loaded.
 */
public class YamlMessageProvider implements MessageProvider {

	private final YamlConfiguration yaml;

	public YamlMessageProvider(YamlConfiguration yaml) {
		this.yaml = yaml;
	}

	@Override
	public String getString(String path) {
		return yaml.getString(path);
	}

	@Override
	public List<String> getStringList(String path) {
		return yaml.getStringList(path);
	}

}
