package me.luckyraven.copsncrooks.npc.trader.trait;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import me.luckyraven.persistence.FileHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

@CustomLog
@RequiredArgsConstructor
public final class TraderTraitsLoader {

	private final TraderTraitRegistry registry;

	public void load(FileHandler fileHandler) {
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
					s.getInt("Anger_Hit_Threshold"),
					s.getDouble("Mood_Per_Hit"),
					s.getDouble("Mood_Per_Tip_Currency"),
					s.getDouble("Mood_Per_Purchase"),
					s.getDouble("Mood_Per_Rejection"),
					s.getDouble("Mood_Decay_Per_Second"),
					s.getDouble("Max_Anger_Multiplier"),
					s.getDouble("Min_Friend_Discount"),
					s.getDouble("Bargain_Min_Ratio"),
					s.getInt("Bargain_Max_Rounds"),
					s.getBoolean("Allows_Bargaining"),
					s.getBoolean("Allows_Barter"),
					sellPriceRatio,
					barterPriceRatio,
					s.getDouble("Max_Health", 20.0D),
					s.getBoolean("Invulnerable", true),
					s.getBoolean("Refunds_Trade_In_Overpay", false),
					s.getBoolean("Market_Linked", true)
			);

			String displayName = s.getString("Display_Name", id);
			return new TraderTraitDefinition(id, displayName, profile);
		} catch (Exception e) {
			log.warn("Trader traits: failed to parse trait '{}': {}", id, e.getMessage());
			return null;
		}
	}

}
