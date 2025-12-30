package me.luckyraven.file.configuration.inventory.lootchest;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.loot.LootChestSettingProvider;

import java.util.List;

public class LootChestSettingAddon implements LootChestSettingProvider {

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
	public List<String> getAllowedBlocks() {
		return SettingAddon.getLootChestAllowedBlocks();
	}

}
