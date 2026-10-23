package org.luckyraven.gangland.copsncrooks.npc.police.perch;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds roof perches overlooking a last known spot for the Marksman's overwatch (0.20.0): columns on rings around the
 * spot, kept when high enough, loaded and with a clear line to it, nearest first.
 *
 * @since 0.20.0
 */
public final class PerchFinder {

	private static final double EYE_HEIGHT = 1.62;

	private PerchFinder() {
	}

	/** Perch standing spots (highest block + 1) for {@code lastKnown}, nearest first; empty without a world. */
	public static List<Location> find(Location lastKnown, OverwatchSettings settings) {
		List<Location> perches = new ArrayList<>();
		World          world   = lastKnown.getWorld();
		if (world == null) return perches;

		double lx = lastKnown.getX();
		double lz = lastKnown.getZ();
		for (int i = 0; i < settings.columns(); i++) {
			double angle  = Math.toRadians(360.0 * i / settings.columns());
			double radius = i % 2 == 0 ? settings.ringMin() : settings.ringMax();
			double x      = lx + radius * Math.cos(angle);
			double z      = lz + radius * Math.sin(angle);

			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

			int y = world.getHighestBlockYAt(bx, bz) + 1;
			if (y < lastKnown.getBlockY() + settings.minHeight()) continue;
			if (!sightClear(world, x, y, z, lastKnown)) continue;

			perches.add(new Location(world, x, y, z));
		}
		perches.sort(Comparator.comparingDouble(p -> Math.hypot(p.getX() - lx, p.getZ() - lz)));
		return perches;
	}

	/** Eye to eye: from 1.62 above the perch toward 1.62 above the last known spot, nothing solid in between. */
	private static boolean sightClear(World world, double x, int y, double z, Location lastKnown) {
		Location start = new Location(world, x, y + EYE_HEIGHT, z);
		Vector   dir   = new Vector(lastKnown.getX() - x, lastKnown.getY() - y, lastKnown.getZ() - z);
		double   len   = dir.length();
		if (len < 1.0E-9) return true;
		return world.rayTraceBlocks(start, dir.normalize(), len, FluidCollisionMode.NEVER, true) == null;
	}
}
