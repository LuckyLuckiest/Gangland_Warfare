package org.luckyraven.gangland.healthbars.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.util.ChatUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code healthbars.yml}, read lazily per call (like the gadget module's {@code JetpackMessages}) so {@code /reload}
 * needs no parse step. Every key has a built-in default: a missing key is never a fault.
 */
public class HealthBarSettings implements FileInitializer {

	static final String FILE_NAME = "healthbars";

	private final FileHandler fileHandler;

	public HealthBarSettings(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		// Keys are read lazily per call - nothing to pre-parse.
	}

	public boolean enabled() {
		return config().getBoolean("Enabled", true);
	}

	/**
	 * The coloured bar line: {@code ceil(Segments * health / max)} filled cells in {@code High_Color} (above half),
	 * {@code Medium_Color} (above a quarter) or {@code Low_Color}, the rest in {@code Empty_Color}, placed into
	 * {@code Format}.
	 */
	public String render(double health, double max) {
		FileConfiguration config   = config();
		int               segments = Math.max(1, config.getInt("Segments", 10));
		String            symbol   = config.getString("Symbol", "|");
		double            fraction = max <= 0 ? 0 : Math.max(0, Math.min(1, health / max));
		int               filled   = (int) Math.ceil(segments * fraction);

		String color = fraction > 0.5 ? config.getString("High_Color", "&a")
		                              : fraction > 0.25 ? config.getString("Medium_Color", "&e")
		                                                : config.getString("Low_Color", "&c");
		String bar = color + symbol.repeat(filled) + config.getString("Empty_Color", "&8")
		             + symbol.repeat(segments - filled);

		return ChatUtil.color(config.getString("Format", "%bar%")
		                            .replace("%bar%", bar)
		                            .replace("%health%", String.format(Locale.ROOT, "%.1f", Math.max(0, health)))
		                            .replace("%max%", String.format(Locale.ROOT, "%.1f", max)));
	}

	private FileConfiguration config() {
		return fileHandler.getFileConfiguration();
	}
}
