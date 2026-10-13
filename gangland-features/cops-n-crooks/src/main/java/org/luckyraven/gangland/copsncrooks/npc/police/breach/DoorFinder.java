package org.luckyraven.gangland.copsncrooks.npc.police.breach;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

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

	public static Optional<Door> find(Location suspect, int radius, BlockLookup lookup) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@code true} when the highest block above the suspect is more than {@code roofBlocks} over his feet. */
	public static boolean indoors(Location suspect, int roofBlocks) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A lookup over {@code world}'s blocks (Tag.DOORS, Bisected half, Directional facing, isPassable, isSolid). */
	public static BlockLookup worldLookup(World world) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
