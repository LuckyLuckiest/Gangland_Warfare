package me.luckyraven.lootchest.config;

import me.luckyraven.util.utilities.messages.TimeMessagesProvider;

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
	 * Sent when no {@link me.luckyraven.item.ItemParser} has been wired into the loot chest service.
	 */
	String getNoItemProvider();

	/**
	 * Sent when the chest has already been looted and is waiting for its cooldown.
	 */
	String getAlreadyLooted();

	/**
	 * First hologram line shown while the chest is on cooldown (e.g. "§c§lON COOLDOWN").
	 */
	String getHologramCooldownStatus();

	/**
	 * First hologram line shown when the chest is available (e.g. "§a§lAVAILABLE").
	 */
	String getHologramAvailableStatus();

	/**
	 * Second hologram line shown when the chest is available (e.g. "§7Right-click to open").
	 */
	String getHologramAvailableHint();

	/**
	 * Time-unit label provider used when formatting the cooldown countdown in chat.
	 */
	TimeMessagesProvider getTimeMessages();
}
