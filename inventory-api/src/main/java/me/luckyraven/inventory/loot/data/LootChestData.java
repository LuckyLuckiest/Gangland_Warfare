package me.luckyraven.inventory.loot.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Represents a placed loot chest in the world
 */
@Getter
@Builder
public class LootChestData {

	private final UUID     id;
	private final Location location;
	private final String   lootTableId;
	private final LootTier tier;
	private final long     respawnTime;
	private final int      inventorySize;
	private final String   displayName;

	// State
	@Setter
	private long    lastOpened;
	@Setter
	private boolean isLooted;
	@Setter
	private long    cooldownEndTime;

	// Persistent inventory state - keeps items until cooldown ends
	@Setter
	private List<ItemStack> currentInventory;
	@Setter
	private int[]           currentSlotMapping;

	// Cracking/Lockpick minigame settings (optional per-chest override)
	@Setter
	@Builder.Default
	private boolean crackingEnabled     = false;
	@Setter
	@Builder.Default
	private long    crackingTimeSeconds = 0;

	public void markAsLooted() {
		this.isLooted   = true;
		this.lastOpened = System.currentTimeMillis();
	}

	/**
	 * Starts the global cooldown timer for this chest
	 *
	 * @param cooldownSeconds cooldown duration in seconds
	 */
	public void startCooldown(long cooldownSeconds) {
		this.cooldownEndTime = System.currentTimeMillis() + (cooldownSeconds * 1000);
		this.isLooted        = true;
	}

	/**
	 * Checks if the chest is currently on cooldown
	 */
	public boolean isOnCooldown() {
		return cooldownEndTime > 0 && System.currentTimeMillis() < cooldownEndTime;
	}

	/**
	 * Gets the remaining cooldown time in seconds
	 */
	public long getRemainingCooldownSeconds() {
		if (!isOnCooldown()) return 0;
		return (cooldownEndTime - System.currentTimeMillis()) / 1000;
	}

	/**
	 * Gets the remaining cooldown time in milliseconds
	 */
	public long getRemainingCooldownMillis() {
		if (!isOnCooldown()) return 0;
		return cooldownEndTime - System.currentTimeMillis();
	}

	/**
	 * Checks if the chest has any items remaining
	 */
	public boolean hasItemsRemaining() {
		if (currentInventory == null) return false;
		return currentInventory.stream().anyMatch(item -> item != null && !item.getType().isAir());
	}

	/**
	 * Checks if the chest is completely empty
	 */
	public boolean isEmpty() {
		return !hasItemsRemaining();
	}

	/**
	 * Checks if the chest should block opening (empty AND on cooldown)
	 */
	public boolean isBlocked() {
		return isEmpty() && isOnCooldown();
	}

	/**
	 * Checks if the chest can be respawned based on old respawnTime logic
	 */
	public boolean canRespawn() {
		// If on cooldown, cannot respawn yet
		if (isOnCooldown()) return false;

		// If cooldown just ended, it can respawn
		if (cooldownEndTime > 0 && System.currentTimeMillis() >= cooldownEndTime) {
			return true;
		}

		// Legacy respawn logic
		if (respawnTime <= 0) return false;
		return System.currentTimeMillis() - lastOpened >= respawnTime * 1000;
	}

	public void respawn() {
		this.isLooted           = false;
		this.cooldownEndTime    = 0;
		this.currentInventory   = null;
		this.currentSlotMapping = null;
	}

	/**
	 * Clears the persistent inventory (used when cooldown ends)
	 */
	public void clearInventory() {
		this.currentInventory   = null;
		this.currentSlotMapping = null;
	}

}
