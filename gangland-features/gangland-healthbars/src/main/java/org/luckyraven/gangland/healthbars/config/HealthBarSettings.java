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
	 * The heart bar line: {@code Hearts} glyphs (default 10), each worth {@code max / Hearts} HP and drawn as
	 * {@code Full_Heart}, {@code Half_Heart} or {@code Empty_Heart}. Health rounds up to the next half heart, so a hurt
	 * NPC never reads as full and a living one never as empty. Placed into {@code Format}.
	 */
	public String render(double health, double max) {
		FileConfiguration config   = config();
		int               hearts   = Math.max(1, Math.min(40, config.getInt("Hearts", 10)));
		double            fraction = max <= 0 ? 0 : Math.max(0, Math.min(1, health / max));
		int               halves   = (int) Math.ceil(hearts * 2 * fraction - 1e-9);
		int               full     = halves / 2;
		int               half     = halves % 2;

		String bar = config.getString("Full_Heart", "&c❤").repeat(full)
		             + config.getString("Half_Heart", "&c♡").repeat(half)
		             + config.getString("Empty_Heart", "&8❤").repeat(hearts - full - half);

		return ChatUtil.color(config.getString("Format", "%bar%")
		                            .replace("%bar%", bar)
		                            .replace("%health%", String.format(Locale.ROOT, "%.1f", Math.max(0, health)))
		                            .replace("%max%", String.format(Locale.ROOT, "%.1f", max)));
	}

	private FileConfiguration config() {
		return fileHandler.getFileConfiguration();
	}
}
