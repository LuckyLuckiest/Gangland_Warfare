package org.luckyraven.gangland.lootchest.config;

import org.luckyraven.keystone.util.messages.TimeMessagesProvider;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.lootchest.data.LootTier;

/**
 * Provides all player-facing messages and hologram text used by the loot chest system.
 * <p>
 * Implementations live in {@code gangland-impl} and read from the configured messages file via {@code MessageAddon}.
 * lootchest-api never accesses message files directly.
 */
public interface LootChestMessagesProvider {
	/**
	 * Sent when the cracking minigame begins.
	 */
	String getCrackingStarted();

	/**
	 * Sent when the player tries to open a chest while already in a session.
	 */
	String getAlreadyInSession();

	/**
	 * Sent when the chest is on cooldown.
	 *
	 * @param formattedTime the remaining time already formatted (e.g. "4m 30s")
	 */
	String getOnCooldown(String formattedTime);

	/**
	 * Sent when the chest requires a lockpick that the player doesn't have.
	 */
	String getRequiresLockpick();

	/**
	 * Sent when the chest requires a key that the player doesn't have.
	 */
	String getRequiresKey();

	/**
	 * Sent when the player lacks permission to open the chest.
	 */
	String getNoPermission();

	/**
	 * Sent when the chest's loot table is misconfigured or missing.
	 */
	String getInvalidLootTable();

	/**
	 * Sent when the chest data itself is invalid or unregistered.
	 */
	String getInvalidChest();

	/**
	 * Sent when no {@link ItemParser} has been wired into the loot chest service.
	 */
	String getNoItemProvider();

	/**
	 * Sent when the chest has already been looted and is waiting for its cooldown.
	 */
	String getAlreadyLooted();

	/**
	 * First hologram line shown while the chest is on cooldown (e.g. "&c&lON COOLDOWN").
	 */
	String getHologramCooldownStatus();

	/**
	 * First hologram line shown when the chest is available (e.g. "&a&lAVAILABLE").
	 */
	String getHologramAvailableStatus();

	/**
	 * Second hologram line shown when the chest is available (e.g. "&7Right-click to open").
	 */
	String getHologramAvailableHint();

	/**
	 * First hologram line for a tiered chest — typically the tier's display name (e.g. "&9Rare").
	 */
	String getHologramTierLabel(LootTier tier);

	/**
	 * Hologram line for a locked chest that requires an item ({@code LOCKPICK} / {@code KEY}).
	 *
	 * @param itemDisplay the tier's {@link LootTier#unlockItemDisplay()}, already colour-coded
	 */
	String getHologramLockedRequires(String itemDisplay);

	/**
	 * Hologram line for a chest locked behind a permission.
	 */
	String getHologramLockedPermission();

	/**
	 * Hologram line shown on a {@code LOCKPICK} / {@code KEY} chest after a player has consumed the unlock item for the
	 * current cycle (subsequent openers don't need to burn another one).
	 */
	String getHologramUnlocked();

	/**
	 * Time-unit label provider used when formatting the cooldown countdown in chat.
	 */
	TimeMessagesProvider getTimeMessages();
}
