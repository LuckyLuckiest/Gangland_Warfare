package org.luckyraven.gangland.fights.arena;

import org.bukkit.Location;
import org.bukkit.World;

/** A spot in a world with a facing: arena spawns and exits. */
public record ArenaPoint(String world, double x, double y, double z, float yaw, float pitch) {

	/** The Bukkit location of this point in {@code in} (the caller resolves, and null-checks, the world by name). */
	public Location toLocation(World in) {
		return new Location(in, x, y, z, yaw, pitch);
	}
}
