package me.luckyraven.weapon.fire;

import me.luckyraven.core.bean.BeanLifecycle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of every {@code Material.FIRE} block placed by the weapon system (incendiary impacts and explosive
 * throwables). The companion {@code PluginFireProtectionListener} consults this set to keep every plugin-spawned fire
 * purely cosmetic — vanilla burn/spread/ignite events involving a tracked block are cancelled, so wood, wool and other
 * flammable terrain is never consumed even when many fires overlap.
 *
 * <p>Keys are {@link BlockVector} instances (integer coordinates) grouped by world UUID; raw {@link
 * org.bukkit.Location} is unsuitable because its {@code equals} considers yaw/pitch.
 */
public class PluginFireRegistry implements BeanLifecycle {

	private static final BlockFace[] FACE_NEIGHBOURS = {
			BlockFace.UP, BlockFace.DOWN,
			BlockFace.NORTH, BlockFace.SOUTH,
			BlockFace.EAST, BlockFace.WEST
	};

	private final Map<UUID, Set<BlockVector>> tracked = new ConcurrentHashMap<>();

	private static BlockVector toKey(Block block) {
		return new BlockVector(block.getX(), block.getY(), block.getZ());
	}

	public void track(Block block) {
		if (block == null) return;
		World world = block.getWorld();
		tracked.computeIfAbsent(world.getUID(), k -> ConcurrentHashMap.newKeySet())
		       .add(toKey(block));
	}

	public void untrack(Block block) {
		if (block == null) return;
		Set<BlockVector> worldSet = tracked.get(block.getWorld().getUID());
		if (worldSet == null) return;
		worldSet.remove(toKey(block));
	}

	public boolean isTracked(Block block) {
		if (block == null) return false;
		Set<BlockVector> worldSet = tracked.get(block.getWorld().getUID());
		return worldSet != null && worldSet.contains(toKey(block));
	}

	/**
	 * True when any of the six face neighbours of {@code block} is a tracked fire. Used as a fallback for
	 * {@code BlockBurnEvent} on Spigot, where {@code getIgnitingBlock()} can be null.
	 */
	public boolean hasTrackedNeighbour(Block block) {
		if (block == null) return false;
		Set<BlockVector> worldSet = tracked.get(block.getWorld().getUID());
		if (worldSet == null || worldSet.isEmpty()) return false;
		for (BlockFace face : FACE_NEIGHBOURS) {
			Block neighbour = block.getRelative(face);
			if (worldSet.contains(toKey(neighbour))) return true;
		}
		return false;
	}

	@Override
	public void onShutdown() {
		tracked.clear();
	}

}
