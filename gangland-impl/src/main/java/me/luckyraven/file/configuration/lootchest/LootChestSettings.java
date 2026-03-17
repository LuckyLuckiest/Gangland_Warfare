package me.luckyraven.file.configuration.lootchest;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.lootchest.config.LootChestSettingsProvider;

import java.util.List;

public class LootChestSettings implements LootChestSettingsProvider {

	@Override
	public long getCountdownTimer() {
		return Settings.getLootChestCountdownTimer();
	}

	@Override
	public String getOpeningSound() {
		return Settings.getLootChestOpeningSound();
	}

	@Override
	public String getLockedSound() {
		return Settings.getLootChestLockedSound();
	}

	@Override
	public String getClosingSound() {
		return Settings.getLootChestClosingSound();
	}

	@Override
	public List<String> getAllowedBlocks() {
		return Settings.getLootChestAllowedBlocks();
	}

}
