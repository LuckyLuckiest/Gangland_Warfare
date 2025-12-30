package me.luckyraven.inventory.loot;

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

	public void markAsLooted() {
		this.isLooted   = true;
		this.lastOpened = System.currentTimeMillis();
	}

	public boolean canRespawn() {
		if (respawnTime <= 0) return false;
		return System.currentTimeMillis() - lastOpened >= respawnTime * 1000;
	}

	public void respawn() {
		this.isLooted = false;
	}
}
