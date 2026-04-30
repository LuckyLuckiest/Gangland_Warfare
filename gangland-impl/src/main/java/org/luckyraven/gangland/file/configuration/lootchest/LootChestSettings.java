package org.luckyraven.gangland.file.configuration.lootchest;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.lootchest.config.LootChestSettingsProvider;

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
