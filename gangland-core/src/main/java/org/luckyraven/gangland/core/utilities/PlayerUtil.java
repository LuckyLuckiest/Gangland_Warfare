package org.luckyraven.gangland.core.utilities;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Server-side player utilities.
 */
public final class PlayerUtil {

	private PlayerUtil() { }

	/**
	 * Hybrid ground check that combines the client-reported {@link Player#isOnGround()} flag with a server-side
	 * bounding-box probe.
	 *
	 * <p>The client flag is accurate for stairs, slabs, fences, trapdoors, and other partial-height blocks where
	 * {@link Block#getBoundingBox()} returns only the outer AABB (so a server probe can't tell the lower step of a
	 * stair from open air). For callers where false-positives only cost the player a self-disadvantage (jetpack thrust
	 * suppression), trusting the client flag is acceptable. Climbable blocks (ladders, vines, scaffolding) are also
	 * treated as ground so airborne checks don't fire while the player is climbing.
	 *
	 * <p>The bounding-box probe is kept as a fallback: it iterates the blocks below each corner and centre of the
	 * player's location and checks whether their top surface is within {@code eps} of the player's feet.
	 *
	 * @param player the player to check
	 *
	 * @return {@code true} if the player is standing on, or climbing, a solid surface
	 */
	@SuppressWarnings("deprecation")
	public static boolean isOnGround(Player player) {
		// Client-reported flag — correctly identifies standing on stairs/slabs/trapdoors. A spoofed `true` only
		// suppresses the player's own jetpack thrust (no exploit surface).
		if (player.isOnGround()) return true;

		Location loc   = player.getLocation();
		World    world = loc.getWorld();
		if (world == null) return false;

		// Climbable blocks (ladder, vines, scaffolding, weeping/twisting vines, cave vines) — the client reports
		// onGround=false on these but the player is functionally stationary while climbing.
		Block at = world.getBlockAt(loc);
		if (Tag.CLIMBABLE.isTagged(at.getType())) return true;

		// Server-side fallback: probe blocks beneath the player's footprint for a top-surface near the feet Y.
		double feet   = loc.getY();
		double offset = 0.29;
		double eps    = 0.1;

		double[] xOff = {0.0, offset, -offset};
		double[] zOff = {0.0, offset, -offset};

		int baseY = (int) Math.floor(feet);

		for (double dx : xOff) {
			for (double dz : zOff) {
				int bx = (int) Math.floor(loc.getX() + dx);
				int bz = (int) Math.floor(loc.getZ() + dz);

				for (int by = baseY; by >= baseY - 1; by--) {
					Block block = world.getBlockAt(bx, by, bz);
					if (block.isPassable()) continue;

					BoundingBox bb = block.getBoundingBox();
					if (Math.abs(bb.getMaxY() - feet) <= eps) {
						return true;
					}
				}
			}
		}

		return false;
	}

}
