package org.luckyraven.gangland.fights.arena;

import org.bukkit.Location;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Arenas by name, backed by {@code fight/arenas.yml}. */
public class ArenaRegistry {

	/** In memory only: nothing is read or written. */
	public ArenaRegistry() {
	}

	public ArenaRegistry(Path file) {
	}

	public Optional<Arena> byName(String name) {
		return Optional.empty();
	}

	public List<Arena> all() {
		return List.of();
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

	public Optional<Arena> overlapping(ArenaBox box) {
		return Optional.empty();
	}

	public boolean add(Arena arena) {
		return false;
	}

	public boolean remove(String name) {
		return false;
	}

	public boolean setEnabled(String name, boolean enabled) {
		return false;
	}

	public void reload() {
	}
}
