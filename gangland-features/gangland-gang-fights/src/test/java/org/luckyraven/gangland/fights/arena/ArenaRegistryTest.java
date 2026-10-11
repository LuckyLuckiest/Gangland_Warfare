package org.luckyraven.gangland.fights.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaRegistry - saved arenas survive a reload")
class ArenaRegistryTest {

	private static final String NL = "\n";

	@TempDir(cleanup = CleanupMode.NEVER)
	Path dir;

	private static Arena arena(String name, int offset) {
		return new Arena(name, ArenaBox.of("w", offset, 60, 0, offset + 9, 69, 9),
		                 List.of(new ArenaPoint("w", offset + 1.5, 60, 1.5, 0f, 0f)),
		                 List.of(new ArenaPoint("w", offset + 8.5, 60, 8.5, 180f, 0f)),
		                 new ArenaPoint("w", 500, 64, 500, 0f, 0f), true);
	}

	@Test
	@DisplayName("an arena built in game survives arena reload and a new registry (acceptance F1)")
	void survivesReload() {
		Path          file     = dir.resolve("a").resolve("arenas.yml");
		ArenaRegistry registry = new ArenaRegistry(file);
		assertTrue(registry.add(arena("Pit", 0)));
		assertTrue(registry.add(arena("Docks", 100)));

		registry.reload();
		assertEquals(List.of("Pit", "Docks"), registry.all().stream().map(Arena::name).toList());
		assertEquals(registry.all(), new ArenaRegistry(file).all());
	}

	@Test
	@DisplayName("reload after a hand edit keeps every good arena and drops only the broken one")
	void handEdit() throws IOException {
		Path          file     = dir.resolve("b.yml");
		ArenaRegistry registry = new ArenaRegistry(file);
		registry.add(arena("Pit", 0));
		registry.add(arena("Docks", 100));

		Files.writeString(file, Files.readString(file) + "  Broken:\n    World: w\n");
		registry.reload();

		assertEquals(List.of("Pit", "Docks"), registry.all().stream().map(Arena::name).toList());
	}

	@Test
	@DisplayName("a skipped hand-edited entry is backed up before the next save rewrites the file")
	void brokenEntryBackedUp() throws IOException {
		Path          file     = dir.resolve("d.yml");
		ArenaRegistry registry = new ArenaRegistry(file);
		registry.add(arena("Pit", 0));
		Files.writeString(file, Files.readString(file) + "  Broken:" + NL + "    World: w" + NL);
		registry.reload();
		registry.add(arena("Docks", 100));

		Path backup = file.resolveSibling("d.yml.broken");
		assertTrue(Files.exists(backup));
		assertTrue(Files.readString(backup).contains("Broken"));
	}

	@Test
	@DisplayName("a file that is not YAML at all keeps the arenas in memory")
	void unreadableFile() throws IOException {
		Path          file     = dir.resolve("c.yml");
		ArenaRegistry registry = new ArenaRegistry(file);
		registry.add(arena("Pit", 0));

		Files.writeString(file, "Arenas: [unclosed\n  : :");
		registry.reload();

		assertEquals(1, registry.all().size());
	}

	@Test
	@DisplayName("lookups are case-insensitive; off hides an arena from enabled() and freeArena()")
	void lookups() {
		ArenaRegistry registry = new ArenaRegistry();
		registry.add(arena("Pit", 0));
		registry.add(arena("Docks", 100));

		assertTrue(registry.byName("pIT").isPresent());
		assertEquals("Pit", registry.freeArena(Set.of()).orElseThrow().name());
		assertEquals("Docks", registry.freeArena(Set.of("Pit")).orElseThrow().name());
		assertTrue(registry.freeArena(Set.of("Pit", "Docks")).isEmpty());

		registry.setEnabled("pit", false);
		assertEquals(List.of("Docks"), registry.enabled().stream().map(Arena::name).toList());
		assertFalse(registry.setEnabled("nope", true));
		assertTrue(registry.remove("DOCKS"));
		assertFalse(registry.remove("DOCKS"));
	}

	@Test
	@DisplayName("the outline stays inside its point budget for a big box")
	void outlineBudget() {
		assertTrue(ArenaOutline.edgePoints(ArenaBox.of("w", 0, 0, 0, 200, 100, 200)).size() <= 1000);
		assertFalse(ArenaOutline.edgePoints(ArenaBox.of("w", 0, 0, 0, 3, 3, 3)).isEmpty());
	}
}
