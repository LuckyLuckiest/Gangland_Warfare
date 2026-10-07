package org.luckyraven.gangland.copsncrooks.station;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * A police station: the anchor dispatch measures an ETA from, and the group of cop spawners its units leave from.
 * Created and changed only through {@link StationRegistry}; the world is kept by name so a station of an unloaded world
 * keeps its row. The district it sits in is never stored (resolved live through {@code PlaceNames}).
 */
@Getter
public final class Station {

	private final int    id;
	private final String name;
	private final String world;
	private final double x;
	private final double y;
	private final double z;
	private final float  yaw;
	/** The jail this station books into, or {@code null}. */
	private @Nullable Integer jailId;

	public Station(int id, String name, String world, double x, double y, double z, float yaw,
	               @Nullable Integer jailId) {
		this.id     = id;
		this.name   = name;
		this.world  = world;
		this.x      = x;
		this.y      = y;
		this.z      = z;
		this.yaw    = yaw;
		this.jailId = jailId;
	}

	void setJailId(@Nullable Integer jailId) {
		this.jailId = jailId;
	}

	/** The anchor, or {@code null} while its world is not loaded. */
	public @Nullable Location getLocation() {
		World loaded = Bukkit.getWorld(world);
		return loaded == null ? null : new Location(loaded, x, y, z, yaw, 0f);
	}
}
