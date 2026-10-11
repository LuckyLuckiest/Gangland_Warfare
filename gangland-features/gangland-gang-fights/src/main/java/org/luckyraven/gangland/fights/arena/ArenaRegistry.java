package org.luckyraven.gangland.fights.arena;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Arenas by name. Stub: empty until the arena lane (W6-A) loads {@code fight/arenas.yml}. */
public class ArenaRegistry {

	public Optional<Arena> byName(String name) {
		return Optional.empty();
	}

	public List<Arena> enabled() {
		return List.of();
	}

	public Optional<Arena> freeArena(Set<String> busyArenaNames) {
		return Optional.empty();
	}

	public Optional<Arena> at(Location location) {
		return Optional.empty();
	}

	public void reload() {
	}
}
