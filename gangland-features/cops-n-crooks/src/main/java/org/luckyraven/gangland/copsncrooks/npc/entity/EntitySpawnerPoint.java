package org.luckyraven.gangland.copsncrooks.npc.entity;

import lombok.Getter;
import org.bukkit.Location;

/**
 * Base class for a stored spawn point used by an {@link EntitySpawner}.
 * <p>
 * Subclasses add entity-specific fields (e.g., type ID, tier).
 */
public abstract class EntitySpawnerPoint {

	@Getter
	private final int      id;
	private       Location location;

	protected EntitySpawnerPoint(int id, Location location) {
		this.id       = id;
		this.location = location.clone();
	}

	public Location getLocation() {
		return location.clone();
	}

	public void setLocation(Location location) {
		this.location = location.clone();
	}
}
