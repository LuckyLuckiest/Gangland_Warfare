package org.luckyraven.gangland.fights.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** An arena being built by one staff member: lives in memory until {@code /glw arena finish} accepts it. */
public final class ArenaDraft {

	private static final Object[] NONE = new Object[0];

	/** The answer of {@link #check}: {@code key} is null when the draft is fine, else the Arenas.* message key. */
	public record Verdict(String key, Object[] pairs) {

		public boolean ok() {
			return key == null;
		}
	}

	private final String           name;
	private final List<ArenaPoint> spawnsA = new ArrayList<>();
	private final List<ArenaPoint> spawnsB = new ArrayList<>();
	private       ArenaPoint       exit;
	private       String           world;
	private       int[]            first;
	private       int[]            second;

	public ArenaDraft(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	/**
	 * Sets the next corner and returns which one it was (1 or 2). The third call starts over at 1; a corner in another
	 * world drops the earlier ones.
	 */
	public int nextCorner(String world, int x, int y, int z) {
		int[] corner = {x, y, z};
		if (!world.equals(this.world) || second != null) {
			this.world = world;
			first      = corner;
			second     = null;
			return 1;
		}
		if (first == null) {
			first = corner;
			return 1;
		}
		second = corner;
		return 2;
	}

	public void addSpawn(boolean sideA, ArenaPoint point) {
		(sideA ? spawnsA : spawnsB).add(point);
	}

	public int spawnCount(boolean sideA) {
		return (sideA ? spawnsA : spawnsB).size();
	}

	public void setExit(ArenaPoint exit) {
		this.exit = exit;
	}

	/** The box once both corners are set. */
	public Optional<ArenaBox> box() {
		if (first == null || second == null) {
			return Optional.empty();
		}
		return Optional.of(ArenaBox.of(world, first[0], first[1], first[2], second[0], second[1], second[2]));
	}

	/** The first thing wrong with the draft, as a message key plus (name, value) placeholder pairs. */
	public Verdict check(int minVolume, ArenaRegistry registry, Predicate<String> worldLoaded) {
		if (registry.byName(name).isPresent()) {
			return new Verdict("Arenas.Finish_Name_Taken", new Object[]{"name", name});
		}
		Optional<ArenaBox> box = box();
		if (box.isEmpty()) {
			return new Verdict("Arenas.Finish_No_Corners", NONE);
		}
		ArenaBox arenaBox = box.get();
		if (!worldLoaded.test(arenaBox.world())) {
			return new Verdict("Arenas.Finish_World_Missing", new Object[]{"world", arenaBox.world()});
		}
		if (arenaBox.volume() < minVolume) {
			return new Verdict("Arenas.Finish_Too_Small", new Object[]{"volume", arenaBox.volume(), "min", minVolume});
		}
		if (spawnsA.isEmpty()) {
			return new Verdict("Arenas.Finish_No_Spawn", new Object[]{"side", "A"});
		}
		if (spawnsB.isEmpty()) {
			return new Verdict("Arenas.Finish_No_Spawn", new Object[]{"side", "B"});
		}
		for (ArenaPoint point : spawnsA) {
			if (!arenaBox.contains(point)) {
				return new Verdict("Arenas.Finish_Spawn_Outside", new Object[]{"side", "A"});
			}
		}
		for (ArenaPoint point : spawnsB) {
			if (!arenaBox.contains(point)) {
				return new Verdict("Arenas.Finish_Spawn_Outside", new Object[]{"side", "B"});
			}
		}
		if (exit == null) {
			return new Verdict("Arenas.Finish_No_Exit", NONE);
		}
		if (!worldLoaded.test(exit.world())) {
			return new Verdict("Arenas.Finish_Exit_World", new Object[]{"world", exit.world()});
		}
		Optional<Arena> other = registry.overlapping(arenaBox);
		if (other.isPresent()) {
			return new Verdict("Arenas.Finish_Overlap", new Object[]{"other", other.get().name()});
		}
		return new Verdict(null, NONE);
	}

	/** The arena this draft describes; call only after {@link #check} said ok. */
	public Arena build() {
		return new Arena(name, box().orElseThrow(), List.copyOf(spawnsA), List.copyOf(spawnsB), exit, true);
	}
}
