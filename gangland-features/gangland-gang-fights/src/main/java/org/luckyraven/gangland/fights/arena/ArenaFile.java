package org.luckyraven.gangland.fights.arena;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** The {@code fight/arenas.yml} format: reading hand-edited files and writing atomically. */
public final class ArenaFile {

	/** The arenas that parsed and one sentence per entry that did not. */
	public record Loaded(List<Arena> arenas, List<String> skipped) {
	}

	private ArenaFile() {
	}

	public static Loaded read(FileConfiguration config) {
		return new Loaded(List.of(), List.of());
	}

	public static YamlConfiguration toConfig(Collection<Arena> arenas) {
		return new YamlConfiguration();
	}

	public static void write(Path file, Collection<Arena> arenas) throws IOException {
	}
}
