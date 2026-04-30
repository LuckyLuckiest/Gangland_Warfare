package org.luckyraven.gangland.turf.data;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * X/Z rectangular region spanning bedrock to sky. Y is ignored — if a player stands over the turf's X/Z footprint, they
 * are in it. Matches the GTA-style intuition specified in the turf spec.
 *
 * <p>Always normalized on construction so {@code minX <= maxX} and
 * {@code minZ <= maxZ} regardless of corner order.
 */
@Getter
public final class CuboidRegion implements Region {

	private final String world;
	private final int    minX;
	private final int    maxX;
	private final int    minZ;
	private final int    maxZ;

	public CuboidRegion(String world, int x1, int z1, int x2, int z2) {
		this.world = world;
		this.minX  = Math.min(x1, x2);
		this.maxX  = Math.max(x1, x2);
		this.minZ  = Math.min(z1, z2);
		this.maxZ  = Math.max(z1, z2);
	}

	@Override
	public boolean contains(Location location) {
		if (location == null) {
			return false;
		}
		World locWorld = location.getWorld();
		if (locWorld == null || !locWorld.getName().equals(this.world)) {
			return false;
		}
		int x = location.getBlockX();
		int z = location.getBlockZ();
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	/**
	 * @return true when this cuboid overlaps {@code other} on the X/Z plane (both in the same world). Used by creation
	 * 		to reject conflicting turfs.
	 */
	public boolean overlaps(CuboidRegion other) {
		if (!this.world.equals(other.world)) {
			return false;
		}
		return minX <= other.maxX && maxX >= other.minX
		       && minZ <= other.maxZ && maxZ >= other.minZ;
	}
}
