package org.luckyraven.gangland.copsncrooks.npc.police.breach;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the door a hidden suspect is behind and the cell outside it where cops stack up (0.21.0 breach). Pure over a
 * {@link BlockLookup}, so it is testable without a world; {@link #worldLookup} adapts a real world.
 *
 * @since 0.21.0
 */
public final class DoorFinder {

	/** What the finder needs to know about one block. */
	public record Probe(boolean lowerDoor, BlockFace facing, boolean passable, boolean solid) {

		public static final Probe AIR   = new Probe(false, BlockFace.SELF, true, false);
		public static final Probe SOLID = new Probe(false, BlockFace.SELF, false, true);
	}

	/** The block lookup; called at most {@code (2 * radius + 1)^3} times for the door scan. */
	@FunctionalInterface
	public interface BlockLookup {

		Probe at(int x, int y, int z);
	}

	/** A door (its lower half) and the stack cell outside it. */
	public record Door(Location door, Location stackCell) {
	}

	private DoorFinder() {
	}

	private record Candidate(int x, int y, int z, int distance, Probe probe) {
	}

	public static Optional<Door> find(Location suspect, int radius, BlockLookup lookup) {
		World world = suspect.getWorld();
		if (world == null) {
			return Optional.empty();
		}

		int sx = suspect.getBlockX();
		int sy = suspect.getBlockY();
		int sz = suspect.getBlockZ();

		List<Candidate> doors = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int x = sx + dx;
					int y = sy + dy;
					int z = sz + dz;
					Probe probe = lookup.at(x, y, z);
					if (probe.lowerDoor()) {
						doors.add(new Candidate(x, y, z, dx * dx + dy * dy + dz * dz, probe));
					}
				}
			}
		}
		doors.sort(Comparator.comparingInt(Candidate::distance));

		for (Candidate door : doors) {
			int[] cell = stackCell(door, sx, sz);
			if (cell == null) {
				continue;
			}
			int cx = cell[0];
			int cy = door.y();
			int cz = cell[1];
			boolean clear = lookup.at(cx, cy, cz).passable() && lookup.at(cx, cy + 1, cz).passable();
			boolean floor = lookup.at(cx, cy - 1, cz).solid();
			if (clear && floor) {
				return Optional.of(new Door(new Location(world, door.x(), door.y(), door.z()),
				                            new Location(world, cx, cy, cz)));
			}
		}
		return Optional.empty();
	}

	/**
	 * The cell one block beyond the door on the side away from the suspect, along the door's axis; {@code null} when
	 * the door does not face a cardinal direction.
	 */
	private static int[] stackCell(Candidate door, int sx, int sz) {
		return switch (door.probe().facing()) {
			case EAST, WEST -> new int[]{door.x() + (door.x() >= sx ? 1 : -1), door.z()};
			case NORTH, SOUTH -> new int[]{door.x(), door.z() + (door.z() >= sz ? 1 : -1)};
			default -> null;
		};
	}

	/** {@code true} when the highest block above the suspect is more than {@code roofBlocks} over his feet. */
	public static boolean indoors(Location suspect, int roofBlocks) {
		World world = suspect.getWorld();
		if (world == null) {
			return false;
		}
		int highest = world.getHighestBlockYAt(suspect.getBlockX(), suspect.getBlockZ());
		return highest - suspect.getBlockY() > roofBlocks;
	}

	/** A lookup over {@code world}'s blocks (Tag.DOORS, Bisected half, Directional facing, isPassable, isSolid). */
	public static BlockLookup worldLookup(World world) {
		return (x, y, z) -> {
			Block block = world.getBlockAt(x, y, z);
			BlockData data = block.getBlockData();
			boolean lowerDoor = Tag.DOORS.isTagged(block.getType())
			                    && data instanceof Bisected bisected
			                    && bisected.getHalf() == Bisected.Half.BOTTOM;
			BlockFace facing = data instanceof Directional directional ? directional.getFacing() : BlockFace.SELF;
			return new Probe(lowerDoor, facing, block.isPassable(), block.getType().isSolid());
		};
	}
}
