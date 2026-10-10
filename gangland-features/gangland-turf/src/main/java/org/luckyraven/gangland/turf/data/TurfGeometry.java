package org.luckyraven.gangland.turf.data;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Bukkit-free geometry for the map and HUD: block distance from a point to a turf, the 8-way compass word between two
 * points and the nearest turf. North is -Z, as in Minecraft.
 */
public final class TurfGeometry {

	private static final String[] WORDS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private TurfGeometry() {
	}

	/** Block distance from (x, z) to the region: 0 inside, else the straight distance to the nearest block of it. */
	public static int distance(CuboidRegion region, int x, int z) {
		int dx = Math.max(Math.max(region.getMinX() - x, 0), x - region.getMaxX());
		int dz = Math.max(Math.max(region.getMinZ() - z, 0), z - region.getMaxZ());
		return (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
	}

	/** 8-way compass word from (fromX, fromZ) to (toX, toZ): N, NE, E, SE, S, SW, W, NW ("" when equal). North = -Z. */
	public static String bearing(int fromX, int fromZ, int toX, int toZ) {
		if (fromX == toX && fromZ == toZ) {
			return "";
		}
		double angle = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
		if (angle < 0) {
			angle += 360;
		}
		int index = (int) Math.round(angle / 45.0) % 8;
		return WORDS[index];
	}

	/**
	 * The nearest turf to (x, z) among turfs (same world assumed), its distance and the bearing to its nearest block;
	 * null when turfs is empty. Ties go to the lower turf id.
	 */
	public static @Nullable Nearest nearest(Collection<Turf> turfs, int x, int z) {
		Turf best         = null;
		int  bestDistance = Integer.MAX_VALUE;
		for (Turf turf : turfs) {
			int distance = distance(turf.getRegion(), x, z);
			if (best == null || distance < bestDistance || (distance == bestDistance && turf.getId() < best.getId())) {
				best         = turf;
				bestDistance = distance;
			}
		}
		if (best == null) {
			return null;
		}
		CuboidRegion region = best.getRegion();
		int          nearX  = clamp(x, region.getMinX(), region.getMaxX());
		int          nearZ  = clamp(z, region.getMinZ(), region.getMaxZ());
		return new Nearest(best, bestDistance, bearing(x, z, nearX, nearZ));
	}

	private static int clamp(int value, int min, int max) {
		return Math.min(max, Math.max(min, value));
	}

	/** The nearest turf, its block distance and the compass word to its nearest block. */
	public record Nearest(Turf turf, int distance, String direction) {
	}
}
