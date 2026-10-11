package org.luckyraven.gangland.fights.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArenaDraft.check - what /glw arena finish refuses")
class ArenaSaveValidationTest {

	private static final int MIN = 64;

	private final ArenaRegistry registry = new ArenaRegistry();

	private static ArenaPoint at(double x, double y, double z) {
		return new ArenaPoint("w", x, y, z, 0f, 0f);
	}

	/** A 10x10x10 box with two spawns per side and an exit outside it: valid until a test breaks one thing. */
	private ArenaDraft valid() {
		ArenaDraft draft = new ArenaDraft("Pit");
		draft.nextCorner("w", 0, 60, 0);
		draft.nextCorner("w", 9, 69, 9);
		draft.addSpawn(true, at(1.5, 60, 1.5));
		draft.addSpawn(true, at(1.5, 60, 3.5));
		draft.addSpawn(false, at(8.5, 60, 1.5));
		draft.addSpawn(false, at(8.5, 60, 3.5));
		draft.setExit(at(50.5, 64, 50.5));
		return draft;
	}

	private ArenaDraft.Verdict check(ArenaDraft draft) {
		return draft.check(MIN, registry, world -> world.equals("w"));
	}

	@Test
	@DisplayName("a complete draft passes and builds an enabled arena with its spawns")
	void valid_passes() {
		ArenaDraft draft = valid();

		assertTrue(check(draft).ok());
		Arena arena = draft.build();
		assertEquals("Pit", arena.name());
		assertEquals(2, arena.spawnsA().size());
		assertEquals(2, arena.spawnsB().size());
		assertTrue(arena.enabled());
		assertEquals(1000, arena.box().volume());
	}

	@Test
	@DisplayName("corners: the third /arena corner starts over, a corner in another world resets the draft")
	void corners() {
		ArenaDraft draft = new ArenaDraft("Pit");
		assertEquals(1, draft.nextCorner("w", 0, 0, 0));
		assertEquals(2, draft.nextCorner("w", 5, 5, 5));
		assertEquals(1, draft.nextCorner("w", 9, 9, 9));
		assertTrue(draft.box().isEmpty());
		assertEquals(2, draft.nextCorner("w", 0, 0, 0));
		assertEquals(1, draft.nextCorner("other", 0, 0, 0));
		assertTrue(draft.box().isEmpty());
	}

	@Test
	@DisplayName("noCorners")
	void noCorners() {
		assertEquals("Arenas.Finish_No_Corners", check(new ArenaDraft("Pit")).key());
	}

	@Test
	@DisplayName("spawnOutside")
	void spawnOutside() {
		ArenaDraft draft = valid();
		draft.addSpawn(false, at(30.5, 60, 1.5));

		assertEquals("Arenas.Finish_Spawn_Outside", check(draft).key());
	}

	@Test
	@DisplayName("missingExit")
	void missingExit() {
		ArenaDraft draft = valid();
		draft.setExit(null);

		assertEquals("Arenas.Finish_No_Exit", check(draft).key());
	}

	@Test
	@DisplayName("exit outside the box is fine, exit in an unloaded world is not")
	void exitWorld() {
		ArenaDraft draft = valid();
		draft.setExit(new ArenaPoint("gone", 0, 64, 0, 0f, 0f));

		assertEquals("Arenas.Finish_Exit_World", check(draft).key());
	}

	@Test
	@DisplayName("tooSmall")
	void tooSmall() {
		ArenaDraft draft = new ArenaDraft("Closet");
		draft.nextCorner("w", 0, 60, 0);
		draft.nextCorner("w", 3, 62, 3);
		draft.addSpawn(true, at(0.5, 60, 0.5));
		draft.addSpawn(false, at(2.5, 60, 2.5));
		draft.setExit(at(50, 64, 50));

		assertEquals("Arenas.Finish_Too_Small", check(draft).key());
	}

	@Test
	@DisplayName("overlap")
	void overlap() {
		registry.add(new Arena("Other", ArenaBox.of("w", 9, 60, 9, 20, 70, 20), List.of(at(10, 60, 10)),
		                       List.of(at(12, 60, 12)), at(0, 64, 0), true));

		ArenaDraft.Verdict verdict = check(valid());

		assertEquals("Arenas.Finish_Overlap", verdict.key());
		assertEquals(List.of("other", "Other"), List.of(verdict.pairs()));
	}

	@Test
	@DisplayName("noSpawnForASide")
	void noSpawnForASide() {
		ArenaDraft draft = new ArenaDraft("Pit");
		draft.nextCorner("w", 0, 60, 0);
		draft.nextCorner("w", 9, 69, 9);
		draft.addSpawn(true, at(1.5, 60, 1.5));
		draft.setExit(at(50, 64, 50));

		ArenaDraft.Verdict verdict = check(draft);

		assertEquals("Arenas.Finish_No_Spawn", verdict.key());
		assertEquals(List.of("side", "B"), List.of(verdict.pairs()));
	}

	@Test
	@DisplayName("a name another arena already took is refused")
	void nameTaken() {
		registry.add(new Arena("pit", ArenaBox.of("w", 100, 60, 100, 110, 70, 110), List.of(at(101, 60, 101)),
		                       List.of(at(105, 60, 105)), at(0, 64, 0), true));

		assertEquals("Arenas.Finish_Name_Taken", check(valid()).key());
	}

	@Test
	@DisplayName("turfReminderPrinted and every refusal has its own sentence in fight_messages.yml")
	void turfReminderPrinted() throws Exception {
		YamlConfiguration messages = new YamlConfiguration();
		try (var in = getClass().getResourceAsStream("/fight/fight_messages.yml")) {
			assertNotNull(in);
			messages.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}

		String saved = messages.getString("Arenas.Saved");
		assertNotNull(saved);
		assertTrue(saved.contains("outside turfs"), saved);
		assertTrue(saved.contains("%name%") && saved.contains("%a%") && saved.contains("%b%"), saved);

		for (String key : List.of("Finish_No_Corners", "Finish_No_Spawn", "Finish_Spawn_Outside", "Finish_No_Exit",
		                          "Finish_Exit_World", "Finish_Too_Small", "Finish_Overlap", "Finish_Name_Taken")) {
			String sentence = messages.getString("Arenas." + key);
			assertNotNull(sentence, key);
			assertTrue(sentence.length() > 10, key);
		}
		assertTrue(messages.getString("Arenas.Finish_Too_Small").contains("%min%"));
		assertTrue(messages.getString("Arenas.Finish_Overlap").contains("%other%"));
		assertTrue(messages.getString("Arenas.Finish_No_Spawn").contains("%side%"));
	}
}
