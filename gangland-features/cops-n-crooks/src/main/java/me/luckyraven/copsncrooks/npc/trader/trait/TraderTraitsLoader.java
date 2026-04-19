package me.luckyraven.copsncrooks.npc.trader.trait;

import lombok.CustomLog;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@CustomLog
public final class TraderTraitsLoader implements BeanLifecycle {

	private final FileHandler         fileHandler;
	private final TraderTraitRegistry registry;

	public TraderTraitsLoader(TraderTraitRegistry registry, FileManager fileManager) {
		this.registry = registry;

		try {
			String fileName = "trader_traits";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		load();
	}

	@Override
	public void onClear() {
		registry.replaceAll(Collections.emptyMap());
	}

	public void load() {
		FileConfiguration cfg = fileHandler.getFileConfiguration();
		if (cfg == null) {
			log.warn("Trader traits could not be loaded; trait registry left untouched");
			return;
		}

		Map<String, TraderTraitDefinition> parsed = new LinkedHashMap<>();

		for (String id : cfg.getKeys(false)) {
			ConfigurationSection section = cfg.getConfigurationSection(id);
			if (section == null) {
				log.warn("Trader traits: entry '{}' is not a section; skipping", id);
				continue;
			}

			TraderTraitDefinition definition = parseDefinition(id, section);
			if (definition != null) parsed.put(id, definition);
		}

		if (parsed.isEmpty()) {
			log.warn("Trader traits parsed to zero traits; keeping previous registry state");
			return;
		}

		registry.replaceAll(parsed);
		log.info("Loaded {} trader trait(s): {}", parsed.size(), parsed.keySet());
	}

	private TraderTraitDefinition parseDefinition(String id, ConfigurationSection s) {
		try {
			double sellPriceRatio   = s.getDouble("Sell_Price_Ratio");
			double barterPriceRatio = s.getDouble("Barter_Price_Ratio", sellPriceRatio);

			TraderTraitProfile profile = new TraderTraitProfile(
					s.getDouble("Mood_Per_Tip_Currency"),
					s.getDouble("Mood_Per_Purchase"),
					s.getDouble("Min_Friend_Discount"),
					s.getBoolean("Allows_Barter"),
					sellPriceRatio,
					barterPriceRatio,
					s.getDouble("Max_Health", 20.0D),
					s.getBoolean("Invulnerable", true)
			);

			String displayName = s.getString("Display_Name", id);
			return new TraderTraitDefinition(id, displayName, profile);
		} catch (Exception e) {
			log.warn("Trader traits: failed to parse trait '{}': {}", id, e.getMessage());
			return null;
		}
	}

}
