package me.luckyraven.loot;

import java.util.List;

public interface LootChestSettingProvider {

	long getCountdownTimer();

	String getOpeningSound();

	String getLockedSound();

	List<String> getAllowedBlocks();

	/**
	 * Whether cracking minigame is enabled globally
	 */
	default boolean isCrackingEnabled() {
		return false;
	}

	/**
	 * Default cracking time in seconds
	 */
	default long getCrackingTime() {
		return 10;
	}

}
