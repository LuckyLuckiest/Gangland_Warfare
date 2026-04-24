package me.luckyraven.copsncrooks.npc.turf;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent payload for a per-turf Quartermaster NPC. One row per turf — the {@code turfId} is the primary key and
 * there is at most one powerup NPC per turf at any time. {@code spawnLocation} is admin-set (via
 * {@code /glw turf powerupnpc set}) and the NPC is re-created at that location on every server boot.
 */
@Getter
public final class TurfPowerupData {

	private final int      turfId;
	private final Location spawnLocation;

	@Setter
	@Nullable
	private String displayName;

	public TurfPowerupData(int turfId, Location spawnLocation, @Nullable String displayName) {
		this.turfId        = turfId;
		this.spawnLocation = spawnLocation;
		this.displayName   = displayName;
	}
}
