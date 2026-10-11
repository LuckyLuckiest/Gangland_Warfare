package org.luckyraven.gangland.fights.arena;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Arenas by name (case-insensitive), backed by {@code fight/arenas.yml}. Every change is written through before it is
 * kept; {@link #reload()} re-reads the file so hand edits are picked up.
 */
@CustomLog
public class ArenaRegistry {

	private final Path                  file;
	private final Map<String, Arena> arenas = new LinkedHashMap<>();

	/** In memory only: nothing is read or written. */
	public ArenaRegistry() {
		this.file = null;
	}

	public ArenaRegistry(Path file) {
		this.file = file;
		reload();
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public synchronized Optional<Arena> byName(String name) {
		return Optional.ofNullable(arenas.get(key(name)));
	}

	public synchronized List<Arena> all() {
		return List.copyOf(arenas.values());
	}

	public synchronized List<Arena> enabled() {
		return arenas.values().stream().filter(Arena::enabled).toList();
	}

	/** The first open arena whose name is not in {@code busyArenaNames}. */
	public synchronized Optional<Arena> freeArena(Set<String> busyArenaNames) {
		return arenas.values().stream().filter(Arena::enabled).filter(a -> !busyArenaNames.contains(a.name())).findFirst();
	}

	/** The arena whose box holds the location, open or not. */
	public synchronized Optional<Arena> at(Location location) {
		if (location == null || location.getWorld() == null) {
			return Optional.empty();
		}
		String world = location.getWorld().getName();
		return arenas.values()
				.stream()
				.filter(a -> a.box().contains(world, location.getX(), location.getY(), location.getZ()))
				.findFirst();
	}

	public synchronized Optional<Arena> overlapping(ArenaBox box) {
		return arenas.values().stream().filter(a -> a.box().overlaps(box)).findFirst();
	}

	/** Adds (or replaces) an arena and saves; false, with nothing changed, when the file could not be written. */
	public synchronized boolean add(Arena arena) {
		return change(() -> arenas.put(key(arena.name()), arena));
	}

	public synchronized boolean remove(String name) {
		return arenas.containsKey(key(name)) && change(() -> arenas.remove(key(name)));
	}

	public synchronized boolean setEnabled(String name, boolean enabled) {
		Arena arena = arenas.get(key(name));
		return arena != null && change(() -> arenas.put(key(name), arena.withEnabled(enabled)));
	}

	/** Re-reads the file. A file that cannot be read at all keeps the arenas in memory. */
	public synchronized void reload() {
		if (file == null || !Files.exists(file)) {
			return;
		}
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | InvalidConfigurationException exception) {
			log.warn("arenas.yml could not be read, keeping the arenas in memory: {}", exception.getMessage());
			return;
		}
		ArenaFile.Loaded loaded = ArenaFile.read(config);
		loaded.skipped().forEach(line -> log.warn("{}", line));
		if (!loaded.skipped().isEmpty()) {
			Path backup = file.resolveSibling(file.getFileName() + ".broken");
			try {
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				log.warn("Skipped entries stay in {} until you fix them; the next save rewrites arenas.yml", backup);
			} catch (IOException exception) {
				log.warn("Could not back up arenas.yml to {}: {}", backup, exception.getMessage());
			}
		}
		arenas.clear();
		loaded.arenas().forEach(a -> arenas.put(key(a.name()), a));
	}

	private boolean change(Runnable mutation) {
		Map<String, Arena> before = new LinkedHashMap<>(arenas);
		mutation.run();
		if (file == null) {
			return true;
		}
		try {
			ArenaFile.write(file, new ArrayList<>(arenas.values()));
			return true;
		} catch (IOException | RuntimeException exception) {
			log.warn("arenas.yml could not be written: {}", exception.getMessage());
			arenas.clear();
			arenas.putAll(before);
			return false;
		}
	}
}
