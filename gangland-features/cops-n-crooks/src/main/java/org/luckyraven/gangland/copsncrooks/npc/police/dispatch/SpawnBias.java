package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PostRing;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

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

	/**
	 * Blocks that only stand under water: a pond bed full of them reads as passable, not liquid, feet. Plain enum
	 * constants, not XMaterial: all exist since 1.13, under the 1.16 floor, and XMaterial's class init needs a server.
	 */
	private static final Set<Material> UNDERWATER_PLANTS = EnumSet.of(Material.SEAGRASS, Material.TALL_SEAGRASS,
	                                                                  Material.KELP, Material.KELP_PLANT,
	                                                                  Material.BUBBLE_COLUMN);

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
		if (spot == null || wet(world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ())))
			return lastSeen.clone();
		return spot.setDirection(facing);
	}

	/** {@code feet} is under water: a liquid, an underwater plant (seagrass, kelp, a bubble column) or waterlogged. */
	private static boolean wet(Block feet) {
		if (feet.isLiquid() || UNDERWATER_PLANTS.contains(feet.getType())) return true;
		return feet.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
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
