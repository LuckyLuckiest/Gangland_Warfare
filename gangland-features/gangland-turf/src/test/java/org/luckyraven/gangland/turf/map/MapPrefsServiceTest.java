package org.luckyraven.gangland.turf.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link MapPrefsService}: defaults, set and clear, and that the auto viewers are a snapshot. */
@DisplayName("MapPrefsService - in-memory map preferences")
class MapPrefsServiceTest {

	private final MapPrefsService prefs = new MapPrefsService();
	private final UUID            alice = UUID.randomUUID();

	@Test
	@DisplayName("auto is off and the turf line is DEFAULT until changed")
	void defaults() {
		assertFalse(prefs.isAuto(alice));
		assertEquals(MapPrefsService.HudMode.DEFAULT, prefs.hudMode(alice));
	}

	@Test
	@DisplayName("set auto and the HUD mode, then clear forgets both")
	void setAndClear() {
		prefs.setAuto(alice, true);
		prefs.setHudMode(alice, MapPrefsService.HudMode.ON);

		assertTrue(prefs.isAuto(alice));
		assertEquals(MapPrefsService.HudMode.ON, prefs.hudMode(alice));

		prefs.clear(alice);

		assertFalse(prefs.isAuto(alice));
		assertEquals(MapPrefsService.HudMode.DEFAULT, prefs.hudMode(alice));
	}

	@Test
	@DisplayName("setting auto off or the HUD mode back to DEFAULT removes the entry")
	void offRemoves() {
		prefs.setAuto(alice, true);
		prefs.setAuto(alice, false);
		prefs.setHudMode(alice, MapPrefsService.HudMode.OFF);
		prefs.setHudMode(alice, MapPrefsService.HudMode.DEFAULT);

		assertFalse(prefs.isAuto(alice));
		assertEquals(MapPrefsService.HudMode.DEFAULT, prefs.hudMode(alice));
		assertTrue(prefs.autoViewers().isEmpty());
	}

	@Test
	@DisplayName("cell size: the default until set, then the player's own, forgotten on clear")
	void cellBlocks_defaultThenStoredThenCleared() {
		UUID bob = UUID.randomUUID();
		prefs.setDefaultCellBlocks(32);
		assertEquals(32, prefs.cellBlocks(alice));

		prefs.setCellBlocks(alice, 8);
		assertEquals(8, prefs.cellBlocks(alice));
		assertEquals(32, prefs.cellBlocks(bob));

		prefs.clear(alice);
		assertEquals(32, prefs.cellBlocks(alice));
	}

	@Test
	@DisplayName("autoViewers is a snapshot that later changes do not touch")
	void autoViewersIsSnapshot() {
		UUID bob = UUID.randomUUID();
		prefs.setAuto(alice, true);

		Set<UUID> snapshot = prefs.autoViewers();
		prefs.setAuto(bob, true);

		assertEquals(Set.of(alice), snapshot);
		assertEquals(Set.of(alice, bob), prefs.autoViewers());
	}
}
