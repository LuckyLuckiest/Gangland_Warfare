package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PostRing;

import java.util.Objects;

/**
 * Where reinforcements should appear after a hand-off (set by the hand-off, consumed by dispatch): ahead of the
 * suspect's heading, until {@code until}.
 *
 * @param heading     the suspect's travel direction (only X/Z are read).
 * @param lastSeen    the squad's last-known spot at the hand-off (newest heading sample), never his live position;
 *                    new units seed it led on along the heading ({@link #lead}).
 * @param until       radio-clock ms the bias stops applying to newly enqueued units.
 * @param coneDegrees half-angle, either side of the heading, of "ahead".
 * @since 0.16.0
 */
public record SpawnBias(Vector heading, Location lastSeen, long until, double coneDegrees) {

	/** Eye height the lead's wall check runs at, as {@link PostRing}'s lanes. */
	private static final double EYE_HEIGHT = 1.6;

	public SpawnBias {
		heading  = Objects.requireNonNull(heading, "heading").clone();
		lastSeen = Objects.requireNonNull(lastSeen, "lastSeen").clone();
	}

	/** The bias still applies at {@code now}. */
	public boolean activeAt(long now) {
		return now < until;
	}

	/**
	 * Where the hand-off units head (0.21.1, owner ruling R5): {@code lastSeen} moved {@code blocks} along the heading's
	 * horizontal direction, stopped a block short of the first solid block on the way (eye height), and set on standing
	 * ground near his height there ({@link PostRing#standingSpot}), facing the heading. {@code lastSeen} itself when the
	 * heading has no horizontal length, a chunk on the way is not loaded (checked before the trace, which would load it),
	 * or there is no dry standing room there: a spot underground, under water or inside a building would start a breach
	 * or an unreachable search where he never went.
	 */
	public Location lead(double blocks) {
		Vector facing = new Vector(heading.getX(), 0, heading.getZ());
		World  world  = lastSeen.getWorld();
		if (facing.lengthSquared() == 0 || world == null) return lastSeen.clone();
		facing.normalize();
		// the trace looks up every block it crosses, which loads an unloaded chunk on the main thread: check them first
		if (!chunksLoaded(world, lastSeen, lastSeen.clone().add(facing.clone().multiply(Math.max(0, blocks)))))
			return lastSeen.clone();

		Location       eye  = lastSeen.clone().add(0, EYE_HEIGHT, 0);
		RayTraceResult wall = blocks > 0 ? world.rayTraceBlocks(eye, facing, blocks, FluidCollisionMode.NEVER, true) : null;
		double         run  = wall == null ? blocks : Math.max(0, wall.getHitPosition().distance(eye.toVector()) - 1);
		Location       to   = lastSeen.clone().add(facing.clone().multiply(run));

		// water and lava are passable, so the standing room found may be a pond's bed: he never went under
		Location spot = PostRing.standingSpot(world, to.getX(), to.getZ(), lastSeen.getBlockY());
		if (spot == null || world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ()).isLiquid())
			return lastSeen.clone();
		return spot.setDirection(facing);
	}

	/** Every chunk in the box spanning {@code from} and {@code to} is loaded (a superset of the chunks a line crosses). */
	private static boolean chunksLoaded(World world, Location from, Location to) {
		int minX = Math.min(from.getBlockX(), to.getBlockX()) >> 4;
		int maxX = Math.max(from.getBlockX(), to.getBlockX()) >> 4;
		int minZ = Math.min(from.getBlockZ(), to.getBlockZ()) >> 4;
		int maxZ = Math.max(from.getBlockZ(), to.getBlockZ()) >> 4;
		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				if (!world.isChunkLoaded(x, z)) return false;
		return true;
	}

	/**
	 * The horizontal angle between the heading and {@code spot - from} is at most {@code coneDegrees}. A heading or an
	 * offset without horizontal length has no direction to compare and counts as ahead.
	 */
	public boolean ahead(Location from, Location spot) {
		Vector toward = new Vector(spot.getX() - from.getX(), 0, spot.getZ() - from.getZ());
		Vector facing = new Vector(heading.getX(), 0, heading.getZ());
		if (toward.lengthSquared() == 0 || facing.lengthSquared() == 0) return true;
		return Math.toDegrees(facing.angle(toward)) <= coneDegrees;
	}
}
