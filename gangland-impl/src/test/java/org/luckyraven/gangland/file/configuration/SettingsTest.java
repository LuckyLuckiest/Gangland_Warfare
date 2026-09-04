package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real {@link Settings#initialize()} passes off hand-built {@code settings.yml} fixtures — the same
 * {@code FileHandler}/{@code FileManager} entry point {@code FileConfig} uses at bootstrap.
 *
 * <p>Pins Observation #19 (core-lifecycle.md, Low risk / High confidence — "verified correct code; the risk is
 * operational, not a defect"): the scalar helpers ({@code str}/{@code intVal}/{@code dbl}/{@code bool}/…) silently
 * substitute a hard-coded default whenever a section or key is absent from the YAML, rather than failing the load
 * or logging per-key. A typo'd/removed section therefore produces working-but-wrong values.
 *
 * <p>{@code Settings}' ~200 fields are process-wide statics with no reset hook (documentation/TESTING.md §4); each
 * test method here re-initializes from its own fixture, so assertions never depend on what an earlier test class
 * in this JVM left behind.
 */
@DisplayName("Settings")
class SettingsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("a full settings.yml populates the scalar getters and the reflection-built settingsMap/settingsPlaceholder")
	void initialize_populatesFromYaml() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Database:
				  Type: mysql
				  Auto_Save:
				    Enable: true
				    Time: 15
				    Debug: false
				  Clean_Up:
				    Time: 45
				""");

		SettingsFixture.initialize(tempDir);

		assertEquals("$", Settings.getMoneySymbol());
		assertEquals("mysql", Settings.getDatabaseType());
		assertEquals(15, Settings.getAutoSaveTime());
		assertEquals(45.0, Settings.getCleanUpTime());
		assertFalse(Settings.isAutoSaveDebug());
		assertTrue(Settings.isAutoSave());

		assertFalse(Settings.getSettingsMap().isEmpty());
		assertEquals("$", Settings.getSettingsMap().get("moneySymbol"),
				"settingsMap is built by reflection over every static field, keyed by the Java field name");
		assertEquals("$", Settings.getSettingsPlaceholder().get("money_symbol"),
				"settingsPlaceholder mirrors settingsMap under a snake_case key");
	}

	@Test
	@DisplayName("Observation #19 (core-lifecycle.md): a wholly missing Database section falls back to hard-coded defaults instead of failing")
	void initialize_missingDatabaseSection_fallsBackToDefaults() throws IOException {
		SettingsFixture.write(tempDir, "Money_Symbol: '$'\n");

		assertDoesNotThrow(() -> SettingsFixture.initialize(tempDir));

		assertEquals("sqlite", Settings.getDatabaseType(), "default when the whole Database section is absent");
		assertEquals(10, Settings.getAutoSaveTime(), "default when Database.Auto_Save is absent");
		assertEquals(30.0, Settings.getCleanUpTime(), "default when Database.Clean_Up is absent");
		assertTrue(Settings.isAutoSave(), "default Auto_Save.Enable when the section is absent");
	}

	@Test
	@DisplayName("Observation #19 (core-lifecycle.md): a present-but-empty Auto_Save section falls back per-key, not per-section")
	void initialize_presentButEmptyAutoSaveSection_fallsBackPerKey() throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Time: 99
				""");

		SettingsFixture.initialize(tempDir);

		assertEquals(99, Settings.getAutoSaveTime(), "the one key that was present is honoured");
		assertTrue(Settings.isAutoSave(), "Enable was absent from the (present) Auto_Save section, so it falls " +
				"back to its own default rather than the whole section defaulting");
	}
}
