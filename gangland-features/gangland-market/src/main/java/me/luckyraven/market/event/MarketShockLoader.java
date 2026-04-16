package me.luckyraven.market.event;

import lombok.CustomLog;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.Objects;

/**
 * FILE-phase bean that loads {@code market_events.yml} and registers every declared shock template with the
 * {@link MarketShockRegistry}. Implements {@link FileInitializer} so the bean pipeline treats it like any other config
 * loader.
 */
@CustomLog
public final class MarketShockLoader implements FileInitializer {

	private static final String FILE_NAME = "market_events";

	private final FileHandler         fileHandler;
	private final MarketShockRegistry registry;

	public MarketShockLoader(FileManager fileManager, MarketShockRegistry registry) {
		this.registry = registry;
		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		registry.clear();

		FileConfiguration config = fileHandler.getFileConfiguration();
		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) {
				continue;
			}

			String targetRaw = section.getString("Target");
			if (targetRaw == null || !targetRaw.contains(":")) {
				log.warn("market_events: skipping '%s' — invalid Target (expected 'Item:<id>' or 'Category:<id>')"
								 .formatted(key));
				continue;
			}

			String[] parts = targetRaw.split(":", 2);
			ShockTarget target = switch (parts[0].trim().toLowerCase()) {
				case "item" -> ShockTarget.item(parts[1].trim());
				case "category" -> ShockTarget.category(parts[1].trim());
				default -> null;
			};
			if (target == null) {
				log.warn("market_events: skipping '%s' — unknown target kind '%s'".formatted(key, parts[0]));
				continue;
			}

			double multiplier      = section.getDouble("Multiplier", 1D);
			long   durationMinutes = section.getLong("Duration_Minutes", 60L);

			MarketShock template = new MarketShock(key, target, multiplier, durationMinutes * 60_000L, 0L);
			registry.registerTemplate(template);
		}

		log.info("Loaded %d market shock template(s).".formatted(registry.templates().size()));
	}

	@Override
	public void clear() {
		registry.clear();
	}
}
