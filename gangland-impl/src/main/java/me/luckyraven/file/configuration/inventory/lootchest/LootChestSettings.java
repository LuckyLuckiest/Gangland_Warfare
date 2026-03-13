package me.luckyraven.file.configuration.inventory.lootchest;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.lootchest.config.LootChestSettingsProvider;

import java.util.List;

public class LootChestSettings implements LootChestSettingsProvider {

	@Override
	public long getCountdownTimer() {
		return SettingAddon.getLootChestCountdownTimer();
	}

	@Override
	public String getOpeningSound() {
		return SettingAddon.getLootChestOpeningSound();
	}

	@Override
	public String getLockedSound() {
		return SettingAddon.getLootChestLockedSound();
	}

	@Override
	public String getClosingSound() {
		return SettingAddon.getLootChestClosingSound();
	}

	@Override
	public List<String> getAllowedBlocks() {
		return SettingAddon.getLootChestAllowedBlocks();
	}

}
