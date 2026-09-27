package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.12 F2: {@code Wanted.Evasion} is additive. A {@code settings.yml} without the block (every existing server) loads
 * with evasion on at the shipped values, configured values are read, and an empty per-star list falls back to the
 * shipped list.
 */
@DisplayName("Settings - Wanted.Evasion")
class SettingsEvasionTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("a settings.yml without Wanted.Evasion loads the shipped defaults")
	void missingBlock_loadsDefaults() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Wanted:
				  Enable: true
				""");

		SettingsFixture.initialize(tempDir);

		assertTrue(Settings.isWantedEvasionEnabled());
		assertEquals(3, Settings.getWantedEvasionLostSightSeconds());
		assertEquals("ONE_STAR", Settings.getWantedEvasionDropMode());
		assertEquals(List.of(40, 60, 90, 130, 180), Settings.getWantedEvasionSearchRadius());
		assertEquals(List.of(10, 20, 30, 45, 60), Settings.getWantedEvasionSecondsToDrop());
		assertEquals(2.0, Settings.getWantedEvasionOutsideZoneSpeed());
		assertEquals(1.5, Settings.getWantedEvasionHideoutSpeed());
	}

	@Test
	@DisplayName("configured values are read; an empty list keeps the shipped per-star values")
	void configuredBlock_isRead() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Wanted:
				  Evasion:
				    Enable: false
				    Lost_Sight_Seconds: 5
				    Drop_Mode: ALL_STARS
				    Search_Radius: [20, 30]
				    Seconds_To_Drop: []
				    Outside_Zone_Speed: 3.0
				    Hideout_Speed: 1.2
				""");

		SettingsFixture.initialize(tempDir);

		assertFalse(Settings.isWantedEvasionEnabled());
		assertEquals(5, Settings.getWantedEvasionLostSightSeconds());
		assertEquals("ALL_STARS", Settings.getWantedEvasionDropMode());
		assertEquals(List.of(20, 30), Settings.getWantedEvasionSearchRadius());
		assertEquals(List.of(10, 20, 30, 45, 60), Settings.getWantedEvasionSecondsToDrop());
		assertEquals(3.0, Settings.getWantedEvasionOutsideZoneSpeed());
		assertEquals(1.2, Settings.getWantedEvasionHideoutSpeed());
	}
}
