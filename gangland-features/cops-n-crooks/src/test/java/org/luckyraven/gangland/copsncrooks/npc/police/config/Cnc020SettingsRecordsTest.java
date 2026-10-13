package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 0.20.0 settings records (lane f2-config): the shipped DEFAULT of each block equals the spec values, and the compact
 * constructors clamp nonsense (negative counts, factors outside 0..1, a pause range turned upside down).
 */
@DisplayName("0.20.0 cops settings records: DEFAULT values and clamps")
class Cnc020SettingsRecordsTest {

	@Test
	@DisplayName("every DEFAULT equals the values in the spec (SPEC sections 4-10, brief yamlKeys)")
	void defaults_equalSpec() {
		assertEquals(new PatrolSettings(true, 2, 64, 96, 30, 24, 3, 6, 1, true), PatrolSettings.DEFAULT);
		assertEquals(new WarningSettings(true, 12.0, 5, 30), WarningSettings.DEFAULT);
		assertEquals(new PullOverSettings(true, 24.0, 12.0, 6, 1.0, 2, 150.0, 120), PullOverSettings.DEFAULT);
		assertEquals(new TrackerSettings(true, 2, 4, 48.0, 6, 150), TrackerSettings.DEFAULT);
		assertEquals(new ScannerSettings(true, 160.0, "CLOCK", "&9Police Scanner", List.of("&7Hear the police radio")),
		             ScannerSettings.DEFAULT);
		assertEquals(new SirenSettings(true, 5, 24.0, "BLOCK_NOTE_BLOCK_BELL", 3.0f, 1.0f), SirenSettings.DEFAULT);
		assertEquals(new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5), SightSettings.DEFAULT);
		assertEquals(new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120),
		             OverwatchSettings.DEFAULT);
		assertEquals(new ScannerMapSettings(true, 1, 10, true, "&9Scanner Map",
		                                    List.of("&7Cops, search zone, hideouts")), ScannerMapSettings.DEFAULT);
		assertEquals(new DisguiseSettings(true, List.of("CARVED_PUMPKIN", "PLAYER_HEAD", "ZOMBIE_HEAD",
		                                                "SKELETON_SKULL", "CREEPER_HEAD"), List.of(),
		                                  List.of("police_helmet", "police_vest"), 2, 2), DisguiseSettings.DEFAULT);
	}

	@Test
	@DisplayName("patrol: negative counts clamp to 0, a radius to 1, the pause max never sits under the min, tier >= 1")
	void patrol_clamps() {
		PatrolSettings p = new PatrolSettings(true, -3, 0, 0, -5, 0, 9, 4, 0, true);

		assertEquals(0, p.copsPerStation());
		assertEquals(1, p.activationRadius());
		assertEquals(1, p.despawnRadius());
		assertEquals(0, p.despawnAfterSeconds());
		assertEquals(1, p.beatRadius());
		assertEquals(9, p.pauseSecondsMin());
		assertEquals(9, p.pauseSecondsMax());
		assertEquals(1, p.tier());
	}

	@Test
	@DisplayName("pull-over: range and speed limit >= 1, comply seconds >= 1, a negative ticket or hold is 0")
	void pullOver_clamps() {
		PullOverSettings p = new PullOverSettings(true, -2.0, -5.0, 0, -1.0, -1, -50.0, -9);

		assertEquals(1.0, p.range());
		assertEquals(1.0, p.speedLimit());
		assertEquals(1, p.complySeconds());
		assertEquals(0.0, p.stopSpeed());
		assertEquals(0, p.stopHoldSeconds());
		assertEquals(0.0, p.ticketAmount());
		assertEquals(0, p.repeatSeconds());
	}

	@Test
	@DisplayName("sight: every factor clamps into 0..1, counts and radii are not negative")
	void sight_clamps() {
		SightSettings s = new SightSettings(true, -1, 2.5, 99, -1.0, 7.0, -3, -4.0, -2, 1.5, -0.5, -5);

		assertEquals(0, s.darkMaxLevel());
		assertEquals(1.0, s.darkFactor());
		assertEquals(0.0, s.dimFactor());
		assertEquals(1.0, s.crowdFactor());
		assertEquals(0, s.crowdMinCivilians());
		assertEquals(0.0, s.crowdRadius());
		assertEquals(0, s.crowdOutOfContactSeconds());
		assertEquals(1.0, s.sneakFactor());
		assertEquals(0.0, s.minFactor());
		assertEquals(0, s.shotResetSeconds());
	}

	@Test
	@DisplayName("overwatch: level, column and failure counts >= 1, a ring never inverted, heights not negative")
	void overwatch_clamps() {
		OverwatchSettings o = new OverwatchSettings(true, 0, "Marksman", 30.0, 20.0, 0, -4, 0.0, 0, -1, -1);

		assertEquals(1, o.minLevel());
		assertEquals(30.0, o.ringMin());
		assertEquals(30.0, o.ringMax());
		assertEquals(1, o.columns());
		assertEquals(0, o.minHeight());
		assertEquals(1, o.maxFailures());
		assertEquals(0, o.reachSeconds());
		assertEquals(0, o.maxSeconds());
	}

	@Test
	@DisplayName("scanner map: blocks per pixel >= 1, refresh >= 1 tick")
	void scannerMap_clamps() {
		ScannerMapSettings m = new ScannerMapSettings(true, 0, -4, true, "n", List.of());

		assertEquals(1, m.blocksPerPixel());
		assertEquals(1, m.refreshTicks());
	}

	@Test
	@DisplayName("list components are copied: a later change to the caller's list does not leak in")
	void lists_areCopied() {
		List<String> lore = new ArrayList<>(List.of("a"));
		ScannerSettings s = new ScannerSettings(true, 1.0, "CLOCK", "n", lore);
		lore.add("b");

		assertEquals(List.of("a"), s.itemLore());
		assertThrows(UnsupportedOperationException.class, () -> s.itemLore().add("c"));

		List<String> masks = new ArrayList<>(List.of("PLAYER_HEAD"));
		DisguiseSettings d = new DisguiseSettings(true, masks, List.of(), List.of(), 2, 2);
		masks.add("X");

		assertEquals(List.of("PLAYER_HEAD"), d.maskItems());
		assertThrows(UnsupportedOperationException.class, () -> d.policeOutfit().add("x"));
	}
}
