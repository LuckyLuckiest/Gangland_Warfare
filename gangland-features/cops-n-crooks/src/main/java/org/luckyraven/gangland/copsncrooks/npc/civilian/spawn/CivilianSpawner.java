package org.luckyraven.gangland.copsncrooks.npc.civilian.spawn;

import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntitySpawnerPoint;

/**
 * A single registered civilian spawn point.
 * <p>
 * A spawner is either a <em>type spawner</em> (non-null {@code typeId}) that spawns individual civilians, or a
 * <em>group spawner</em> (non-null {@code groupId}) that spawns a full civilian group from civilians.yml.
 * Both fields being null causes the proximity system to fall back to the configured default type.
 */
@Getter
public class CivilianSpawner extends EntitySpawnerPoint {

	private final @Nullable String typeId;
	private final @Nullable String groupId;

	public CivilianSpawner(int id, Location location, @Nullable String typeId, @Nullable String groupId) {
		super(id, location);
		this.typeId  = typeId;
		this.groupId = groupId;
	}
}
