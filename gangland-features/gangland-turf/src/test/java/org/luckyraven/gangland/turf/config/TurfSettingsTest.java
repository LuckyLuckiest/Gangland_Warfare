package org.luckyraven.gangland.turf.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TurfSettings}: the shipped {@code turf/turf_settings.yml} reproduces the old code defaults, a tuned legacy
 * {@code settings.yml} still wins while the module file holds the default, and a tuned module value wins.
 */
@DisplayName("TurfSettings - turf/turf_settings.yml with settings.yml fallback")
class TurfSettingsTest {

	private YamlConfiguration module;
	private YamlConfiguration legacy;
	private FileHandler       moduleFile;
	private FileManager       fileManager;

	@BeforeEach
	void setUp() throws Exception {
		module = new YamlConfiguration();
		legacy = new YamlConfiguration();

		moduleFile = mock(FileHandler.class);
		when(moduleFile.getFileConfiguration()).thenReturn(module);
		when(moduleFile.getDirectory()).thenReturn("turf/turf_settings");
		when(moduleFile.getFileType()).thenReturn(".yml");

		FileHandler settingsFile = mock(FileHandler.class);
		when(settingsFile.getFileConfiguration()).thenReturn(legacy);

		fileManager = mock(FileManager.class);
		when(fileManager.getFile("settings")).thenReturn(settingsFile);
	}

	private TurfSettings load() {
		return new TurfSettings(moduleFile, fileManager);
	}

	private void loadShippedDefaults() throws Exception {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("turf/turf_settings.yml")) {
			assertNotNull(in, "turf/turf_settings.yml must ship in the module jar");
			module.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	@DisplayName("the shipped file loads every default the old Settings loader used")
	void shippedDefaults() throws Exception {
		loadShippedDefaults();
		TurfSettings s = load();

		assertEquals(10, s.getIncomeIntervalMinutes());
		assertEquals(0, new BigDecimal("100").compareTo(s.getDefaultIncomeAmount()));
		assertEquals("CARROT_ON_A_STICK", s.getWandItemType());
		assertEquals(30, s.getVisualizationDurationSeconds());
		assertEquals("FLAME", s.getVisualizationParticle());
		assertEquals(true, s.isShowEnterTitle());
		assertEquals(180, s.getCaptureDurationSeconds());
		assertEquals(90, s.getCaptureUnclaimedPhase1Seconds());
		assertEquals(90, s.getCaptureUnclaimedPhase2Seconds());
		assertEquals(15, s.getCaptureCooldownMinutes());
		assertEquals(15, s.getCaptureAbandonGraceSeconds());
		assertEquals(10, s.getCapturePostLogoffProtectionMinutes());
		assertEquals(10, s.getCaptureInactivityAutoReleaseDays());
		assertEquals(true, s.isCaptureSoundEnabled());
		assertEquals(true, s.isCaptureBroadcastGlobally());
		assertEquals(List.of(25, 50, 75), s.getCaptureProgressMilestones());
		assertEquals(new TurfSettings.Tone("BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f), s.getStartSound());
		assertEquals(new TurfSettings.Tone("UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f), s.getCompleteSound());
		assertEquals(new TurfSettings.Tone("ENTITY_VILLAGER_NO", 1.0f, 1.0f), s.getFailedSound());
		assertEquals(new TurfSettings.Tone("BLOCK_NOTE_BLOCK_HAT", 0.3f, 1.8f), s.getTickSound());
		assertEquals(new TurfSettings.Tone("ENTITY_ENDER_DRAGON_GROWL", 0.5f, 1.5f), s.getUnclaimedSound());
		assertEquals(0.5, s.getContributionDefenderPresenceTick());
		assertEquals(1.0, s.getContributionAttackerPresenceTick());
		assertEquals(50.0, s.getContributionCaptureCompleteBonus());
		assertEquals(25.0, s.getContributionDefenseSuccessBonus());
	}

	@Test
	@DisplayName("a tuned settings.yml value wins while turf_settings.yml still holds the default")
	void legacyWins() throws Exception {
		loadShippedDefaults();
		legacy.set("Turf.Income_Interval_Minutes", 3);
		legacy.set("Turf.Capture.Sounds.Tick.Pitch", 2.0);
		legacy.set("Turf.Capture.Progress_Milestones", List.of(10, 90));
		legacy.set("Turf.Default_Income_Amount", 250.5);

		TurfSettings s = load();

		assertEquals(3, s.getIncomeIntervalMinutes());
		assertEquals(2.0f, s.getTickSound().pitch());
		assertEquals(List.of(10, 90), s.getCaptureProgressMilestones());
		assertEquals(0, new BigDecimal("250.5").compareTo(s.getDefaultIncomeAmount()));
		assertEquals(180, s.getCaptureDurationSeconds());
	}

	@Test
	@DisplayName("a tuned turf_settings.yml value beats settings.yml")
	void moduleWins() throws Exception {
		loadShippedDefaults();
		legacy.set("Turf.Income_Interval_Minutes", 3);
		module.set("Income_Interval_Minutes", 20);
		module.set("Capture.Progress_Milestones", List.of(50));

		TurfSettings s = load();

		assertEquals(20, s.getIncomeIntervalMinutes());
		assertEquals(List.of(50), s.getCaptureProgressMilestones());
	}

	@Test
	@DisplayName("an empty milestone list falls back to 25/50/75")
	void emptyMilestones() {
		module.set("Capture.Progress_Milestones", List.of());

		assertEquals(List.of(25, 50, 75), load().getCaptureProgressMilestones());
	}
}
