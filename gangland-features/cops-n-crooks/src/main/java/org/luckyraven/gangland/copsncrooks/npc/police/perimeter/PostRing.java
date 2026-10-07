package org.luckyraven.gangland.copsncrooks.npc.police.perimeter;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the spots of a containment perimeter: eight candidates on a ring around the zone centre, kept when the chunk is
 * loaded, there is standing room near the height of the centre and a lane toward the centre is clear.
 *
 * @since 0.16.0
 */
public final class PostRing {

	/** Candidate angles in preference order: opposite corners first, so two posts cover both diagonals. */
	private static final int[]  ANGLES       = {45, 225, 135, 315, 0, 180, 90, 270};
	private static final int    MIN_SPACING  = 90;
	private static final int    FLOOR_SEARCH = 8;
	private static final double EYE_HEIGHT   = 1.6;

	private PostRing() {
	}

	/**
	 * Up to {@code count} spots on the ring of {@code radius} around {@code centre}, at least 90 degrees apart, in
	 * preference order; each is on a floor with two air blocks above and a lane of {@code min(laneLength, radius)}
	 * blocks free toward the centre.
	 */
	public static List<Location> find(Location centre, double radius, int count, double laneLength) {
		List<Location> spots  = new ArrayList<>();
		List<Integer>  angles = new ArrayList<>();
		World          world  = centre.getWorld();
		if (world == null || count <= 0) return spots;

		for (int angle : ANGLES) {
			if (spots.size() >= count) break;
			if (!spaced(angles, angle)) continue;

			double x = centre.getX() + radius * Math.cos(Math.toRadians(angle));
			double z = centre.getZ() + radius * Math.sin(Math.toRadians(angle));
			if (!world.isChunkLoaded(floor(x) >> 4, floor(z) >> 4)) continue;

			Location spot = standingSpot(world, x, z, centre.getBlockY());
			if (spot == null || !laneClear(world, spot, centre, Math.min(laneLength, radius))) continue;

			spots.add(spot);
			angles.add(angle);
		}
		return spots;
	}

	private static boolean spaced(List<Integer> kept, int angle) {
		for (int other : kept) {
			int diff = Math.abs(angle - other) % 360;
			if (Math.min(diff, 360 - diff) < MIN_SPACING) return false;
		}
		return true;
	}

	/** The nearest y to {@code aroundY} (within 8) with a solid block below and two passable blocks at and above it. */
	private static Location standingSpot(World world, double x, double z, int aroundY) {
		int bx = floor(x);
		int bz = floor(z);
		for (int dy = 0; dy <= FLOOR_SEARCH; dy++) {
			for (int y : dy == 0 ? new int[]{aroundY} : new int[]{aroundY + dy, aroundY - dy}) {
				if (!world.getBlockAt(bx, y - 1, bz).isPassable() && world.getBlockAt(bx, y, bz).isPassable()
				    && world.getBlockAt(bx, y + 1, bz).isPassable()) return new Location(world, x, y, z);
			}
		}
		return null;
	}

	private static boolean laneClear(World world, Location spot, Location centre, double length) {
		Vector from = spot.toVector().add(new Vector(0, EYE_HEIGHT, 0));
		Vector dir  = centre.toVector().add(new Vector(0, EYE_HEIGHT, 0)).subtract(from);
		if (dir.lengthSquared() < 1.0E-9) return true;
		return world.rayTraceBlocks(from.toLocation(world), dir.normalize(), length, FluidCollisionMode.NEVER, true) == null;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
