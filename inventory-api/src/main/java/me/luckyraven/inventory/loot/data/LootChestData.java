package me.luckyraven.inventory.loot.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

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
		this.isLooted        = false;
		this.cooldownEndTime = 0;
	}

}
