package org.luckyraven.gangland.fights.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** An arena being built by one staff member: lives in memory until {@code /glw arena finish} accepts it. */
public final class ArenaDraft {

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

	public ArenaDraft(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	public int nextCorner(String world, int x, int y, int z) {
		return 0;
	}

	public void addSpawn(boolean sideA, ArenaPoint point) {
	}

	public int spawnCount(boolean sideA) {
		return 0;
	}

	public void setExit(ArenaPoint exit) {
		this.exit = exit;
	}

	public Optional<ArenaBox> box() {
		return Optional.empty();
	}

	public Verdict check(int minVolume, ArenaRegistry registry, Predicate<String> worldLoaded) {
		return new Verdict("Arenas.Finish_No_Corners", new Object[0]);
	}

	public Arena build() {
		return null;
	}
}
