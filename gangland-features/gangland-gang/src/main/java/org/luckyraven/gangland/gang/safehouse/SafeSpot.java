package org.luckyraven.gangland.gang.safehouse;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

/**
 * Whether a player can stand at a location without taking damage on arrival: two passable blocks (feet and head), a
 * solid floor below them, and none of lava, fire, magma or a cactus in the three blocks. Water is fine to stand in.
 * Below the world floor is the void, so it is refused.
 *
 * <p>ponytail: lives in the gang module; Keystone is the promotion target the day a second module needs a spot check.
 */
public final class SafeSpot {

	private static final Set<XMaterial> HARMFUL = EnumSet.of(XMaterial.LAVA, XMaterial.FIRE, XMaterial.SOUL_FIRE,
	                                                         XMaterial.MAGMA_BLOCK, XMaterial.CACTUS,
	                                                         XMaterial.CAMPFIRE, XMaterial.SOUL_CAMPFIRE,
	                                                         XMaterial.SWEET_BERRY_BUSH, XMaterial.WITHER_ROSE);

	private SafeSpot() {
	}

	public static boolean isSafe(Location location) {
		World world = location.getWorld();
		if (world == null) return false;

		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();
		// standing on the top face of a partial block (slab, carpet, path): that block's cell is the floor, not the feet
		Block cell = world.getBlockAt(x, y, z);
		if (!cell.isPassable() && location.getY() > y)
		{
			y++;
		}

		Block feet  = world.getBlockAt(x, y, z);
		Block head  = world.getBlockAt(x, y + 1, z);
		Block floor = world.getBlockAt(x, y - 1, z);

		if (harmful(feet) || harmful(head) || harmful(floor)) return false;
		if (!feet.isPassable() || !head.isPassable()) return false;

		// a floor of water is a swim, not a fall; an air floor is a drop
		return !floor.isPassable() || floor.isLiquid();
	}

	private static boolean harmful(Block block) {
		return HARMFUL.contains(XMaterial.matchXMaterial(block.getType()));
	}
}
