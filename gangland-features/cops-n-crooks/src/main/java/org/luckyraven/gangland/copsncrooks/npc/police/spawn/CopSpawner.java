package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.entity.EntitySpawnerPoint;

public class CopSpawner extends EntitySpawnerPoint {

	/** The police station this spawner belongs to (dispatch groups units by it); {@code null} = none. */
	@Getter
	@Setter
	private @Nullable Integer stationId;

	public CopSpawner(int id, Location location) {
		super(id, location);
	}
}
