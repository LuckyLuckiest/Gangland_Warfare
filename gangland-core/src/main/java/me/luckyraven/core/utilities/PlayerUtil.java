package me.luckyraven.core.utilities;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * Server-side player utilities that do not rely on client-reported state.
 */
public final class PlayerUtil {

	private PlayerUtil() { }

	/**
	 * Server-side ground check that does not rely on the client-reported {@link Player#isOnGround()} flag, which is
	 * vulnerable to spoofing.
	 *
	 * <p>Inspects the block collision boxes directly below each corner and the
	 * centre of the player's bounding box to determine whether the player's feet are resting on a collidable surface
	 * within a small tolerance. Works correctly for full blocks, slabs, stairs, fences, and other partial-height
	 * blocks.
	 *
	 * @param player the player to check
	 *
	 * @return {@code true} if the player is standing on a solid surface
	 */
	public static boolean isOnGround(Player player) {
		Location loc  = player.getLocation();
		double   feet = loc.getY();
		// Half of the player's width (0.6) with a small inward margin to avoid
		// false positives from blocks that are merely adjacent, not underfoot.
		double offset = 0.29;
		// Tolerance for floating-point imprecision in block collision shapes.
		double eps = 0.05;

		double[] xOff = {0.0, offset, -offset};
		double[] zOff = {0.0, offset, -offset};

		// Start at the block the feet coordinate falls in, then check one block
		// below — necessary for full blocks whose top face equals the player Y.
		int baseY = (int) Math.floor(feet);

		for (double dx : xOff) {
			for (double dz : zOff) {
				int bx = (int) Math.floor(loc.getX() + dx);
				int bz = (int) Math.floor(loc.getZ() + dz);

				for (int by = baseY; by >= baseY - 1; by--) {
					World world = loc.getWorld();
					if (world == null) continue;
					Block block = world.getBlockAt(bx, by, bz);

					// Skip blocks with no collision shape (air, flowers, liquids, etc.)
					if (block.isPassable()) {
						continue;
					}

					// getBoundingBox() returns world-coordinate bounds, so maxY is the
					// exact top surface height.  If the player's feet sit on that surface
					// (within eps) the player is on the ground.
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
