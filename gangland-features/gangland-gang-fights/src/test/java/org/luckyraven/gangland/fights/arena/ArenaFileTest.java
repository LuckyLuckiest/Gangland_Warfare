package org.luckyraven.gangland.fights.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaFile - fight/arenas.yml format")
class ArenaFileTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path dir;

	private static Arena pit(String name, boolean enabled) {
		return new Arena(name, ArenaBox.of("arena_world", -5, 60, -5, 4, 69, 4),
		                 List.of(new ArenaPoint("arena_world", -3.5, 60, 0.5, 90f, 0f),
		                         new ArenaPoint("arena_world", -3.5, 60, 2.5, 90f, 10f)),
		                 List.of(new ArenaPoint("arena_world", 3.5, 60, 0.5, -90f, 0f)),
		                 new ArenaPoint("world", 100.5, 64, -20.5, 180f, 5f), enabled);
	}

	private static YamlConfiguration yaml(String text) throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString(text);
		return config;
	}

	@Test
	@DisplayName("roundTrip")
	void roundTrip() throws Exception {
		Arena first  = pit("Pit", true);
		Arena second = pit("Docks", false);

		ArenaFile.Loaded loaded = ArenaFile.read(yaml(ArenaFile.toConfig(List.of(first, second)).saveToString()));

		assertTrue(loaded.skipped().isEmpty(), loaded.skipped().toString());
		assertEquals(List.of(first, second), loaded.arenas());
	}

	@Test
	@DisplayName("the file is block style with Capitalized_Underscore keys")
	void blockStyle() {
		String text = ArenaFile.toConfig(List.of(pit("Pit", true))).saveToString();

		assertTrue(text.contains("Arenas:"), text);
		assertTrue(text.contains("World: arena_world"), text);
		assertTrue(text.contains("Enabled: true"), text);
		assertTrue(text.contains("Spawns:"), text);
		assertFalse(text.contains("{"), text);
	}

	@Test
	@DisplayName("malformedEntry_skippedOthersLoad")
	void malformedEntry_skippedOthersLoad() throws Exception {
		YamlConfiguration config = ArenaFile.toConfig(List.of(pit("Pit", true), pit("NoExit", true),
		                                                      pit("Docks", true)));
		config.set("Arenas.NoExit.Exit", null);
		config.set("Arenas.Broken.World", "arena_world");
		config.set("Arenas.Broken.Box.Min.X", "not a number");

		ArenaFile.Loaded loaded = ArenaFile.read(yaml(config.saveToString()));

		assertEquals(List.of("Pit", "Docks"), loaded.arenas().stream().map(Arena::name).toList());
		assertEquals(2, loaded.skipped().size());
		assertTrue(loaded.skipped().get(0).contains("NoExit"), loaded.skipped().toString());
		assertTrue(loaded.skipped().get(1).contains("Broken"), loaded.skipped().toString());
	}

	@Test
	@DisplayName("a missing Arenas section is simply no arenas")
	void emptyFile() throws Exception {
		ArenaFile.Loaded loaded = ArenaFile.read(yaml("# only a comment\n"));

		assertTrue(loaded.arenas().isEmpty());
		assertTrue(loaded.skipped().isEmpty());
	}

	@Test
	@DisplayName("atomicWrite_noPartialFile")
	void atomicWrite_noPartialFile() throws IOException {
		Path file = dir.resolve("fight").resolve("arenas.yml");
		ArenaFile.write(file, List.of(pit("Pit", true)));
		String firstText = Files.readString(file);
		assertTrue(firstText.contains("Pit"), firstText);

		ArenaFile.write(file, List.of(pit("Pit", true), pit("Docks", true)));

		assertTrue(Files.readString(file).contains("Docks"));
		try (Stream<Path> files = Files.list(file.getParent())) {
			assertEquals(List.of("arenas.yml"), files.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test
	@DisplayName("a failed write leaves the old file untouched")
	void failedWrite_keepsOldFile() throws IOException {
		Path file = dir.resolve("kept.yml");
		ArenaFile.write(file, List.of(pit("Pit", true)));
		String before = Files.readString(file);
		assertTrue(before.contains("Pit"), before);

		Arena poisoned = new Arena("Bad", null, List.of(), List.of(), null, true);
		try {
			ArenaFile.write(file, List.of(poisoned));
		} catch (RuntimeException | IOException expected) {
			// either is fine; the old content must survive
		}

		assertEquals(before, Files.readString(file));
	}
}
