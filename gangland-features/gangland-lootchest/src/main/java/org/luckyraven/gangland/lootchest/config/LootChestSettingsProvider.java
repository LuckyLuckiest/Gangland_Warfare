package org.luckyraven.gangland.lootchest.config;

import java.util.List;

public interface LootChestSettingsProvider {

	long getCountdownTimer();

	String getOpeningSound();

	String getLockedSound();

	String getClosingSound();

	List<String> getAllowedBlocks();

	/**
	 * Minimum money reward for opening a loot chest (C4 — {@code LootChestEarnGoodsListener} used to read
	 * {@code Settings.getLootChestRewardMoneyMinimum()} directly; G4 moves these 5 onto the provider so the
	 * listener never touches core {@code Settings} statics for loot-chest values).
	 */
	double getRewardMoneyMinimum();

	/**
	 * Maximum money reward for opening a loot chest.
	 */
	double getRewardMoneyMaximum();

	/**
	 * Minimum experience reward for opening a loot chest.
	 */
	double getRewardExperienceMinimum();

	/**
	 * Maximum experience reward for opening a loot chest.
	 */
	double getRewardExperienceMaximum();

	/**
	 * Console commands run (as the console) when a player opens a loot chest.
	 */
	List<String> getRewardCommands();

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
