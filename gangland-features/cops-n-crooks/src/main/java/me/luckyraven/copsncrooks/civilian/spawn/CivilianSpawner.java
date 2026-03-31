package me.luckyraven.copsncrooks.civilian.spawn;

import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntitySpawnerPoint;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * A single registered civilian spawn point.
 * <p>
 * {@code typeId} optionally pins which type is spawned here; {@code null} defers to whatever the caller requests at
 * spawn time.
 */
@Getter
public class CivilianSpawner extends EntitySpawnerPoint {

	private final @Nullable String typeId;

	public CivilianSpawner(int id, Location location, @Nullable String typeId) {
		super(id, location);
		this.typeId = typeId;
	}
}
