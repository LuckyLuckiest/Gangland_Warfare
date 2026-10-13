package org.luckyraven.gangland.lootchest.config;

import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link LootChestSettingsProvider} implementation backed by the module's own
 * {@code lootchests/loot_chest_settings.yml} (WS3 G4 — moved off {@code gangland-api}'s {@code Settings}; module-owned
 * config never lives in the core settings file). Mirrors {@code JetpackMessages}'s {@link FileInitializer} shape.
 */
public class LootChestSettings implements LootChestSettingsProvider, FileInitializer {

	private final FileHandler fileHandler;

	public LootChestSettings(FileManager fileManager) {
		try {
			String fileName = "loot_chest_settings";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
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
		// Flat/list values read lazily per call — nothing to pre-parse.
	}

	@Override
	public long getCountdownTimer() {
		return fileHandler.getFileConfiguration().getLong("Countdown_Timer", 300);
	}

	@Override
	public String getOpeningSound() {
		return fileHandler.getFileConfiguration().getString("Sound.Opening", "BLOCK_CHEST_OPEN");
	}

	@Override
	public String getLockedSound() {
		return fileHandler.getFileConfiguration().getString("Sound.Locked", "BLOCK_CHEST_LOCKED");
	}

	@Override
	public String getClosingSound() {
		return fileHandler.getFileConfiguration().getString("Sound.Closing", "BLOCK_CHEST_CLOSE");
	}

	@Override
	public List<String> getAllowedBlocks() {
		return fileHandler.getFileConfiguration().getStringList("Allowed_Blocks");
	}

	@Override
	public double getRewardMoneyMinimum() {
		return fileHandler.getFileConfiguration().getDouble("Rewards.Money.Minimum", 10);
	}

	@Override
	public double getRewardMoneyMaximum() {
		return fileHandler.getFileConfiguration().getDouble("Rewards.Money.Maximum", 1_000);
	}

	@Override
	public double getRewardExperienceMinimum() {
		return fileHandler.getFileConfiguration().getDouble("Rewards.Experience.Minimum", 5);
	}

	@Override
	public double getRewardExperienceMaximum() {
		return fileHandler.getFileConfiguration().getDouble("Rewards.Experience.Maximum", 100);
	}

	@Override
	public List<String> getRewardCommands() {
		return fileHandler.getFileConfiguration().getStringList("Rewards.Commands");
	}

	@Override
	public CrackingAlarmSettings getCrackingAlarm() {
		var cfg = fileHandler.getFileConfiguration();

		boolean enabled = cfg.getBoolean("Cracking_Alarm.Enable", true);
		Set<String> tiers = cfg.isList("Cracking_Alarm.Tiers")
				? new HashSet<>(cfg.getStringList("Cracking_Alarm.Tiers"))
				: CrackingAlarmSettings.DEFAULT.tiers();
		String sound = cfg.getString("Cracking_Alarm.Sound", CrackingAlarmSettings.DEFAULT.sound());

		return new CrackingAlarmSettings(enabled, tiers, sound);
	}

}
