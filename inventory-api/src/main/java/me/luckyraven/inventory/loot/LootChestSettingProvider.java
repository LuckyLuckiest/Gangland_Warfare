package me.luckyraven.inventory.loot;

import java.util.List;

public interface LootChestSettingProvider {

	long getCountdownTimer();

	String getOpeningSound();

	String getLockedSound();

	List<String> getAllowedBlocks();

}
