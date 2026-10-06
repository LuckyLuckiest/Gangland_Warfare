package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every branch of the {@link MovedSetting} rule: the settings.yml value wins only when it is set, differs from the
 * default and the module file still holds the default (set to it or absent). When settings.yml does not set the old
 * path, the newest Keystone backup ({@code settings-old*.yml}) written on the version-bump regeneration stands in.
 */
@DisplayName("MovedSetting - one-release settings.yml fallback")
class MovedSettingTest {

	private YamlConfiguration module;
	private YamlConfiguration legacy;
	private MovedSetting      moved;

	@TempDir
	Path dataFolder;

	@BeforeEach
	void setUp() {
		module = new YamlConfiguration();
		legacy = new YamlConfiguration();

		FileHandler moduleFile = mock(FileHandler.class);
		when(moduleFile.getFileConfiguration()).thenReturn(module);

		FileHandler settings = mock(FileHandler.class);
		when(settings.getFileConfiguration()).thenReturn(legacy);
		when(settings.getFile()).thenReturn(dataFolder.resolve("settings.yml").toFile());
		when(settings.getName()).thenReturn("settings");
		when(settings.getFileType()).thenReturn("yml");

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("settings")).thenReturn(settings);

		moved = MovedSetting.of(moduleFile, fileManager, "turf");
	}

	// ── int ─────────────────────────────────────────────────────────────────

	@Test
	void int_nothingSet_default() {
		assertEquals(5, moved.getInt("New", "Old", 5));
	}

	@Test
	void int_moduleSet_noLegacy_module() {
		module.set("New", 9);
		assertEquals(9, moved.getInt("New", "Old", 5));
	}

	@Test
	void int_legacyTuned_moduleAbsent_legacy() {
		legacy.set("Old", 7);
		assertEquals(7, moved.getInt("New", "Old", 5));
	}

	@Test
	void int_legacyTuned_moduleSetToDefault_legacy() {
		module.set("New", 5);
		legacy.set("Old", 7);
		assertEquals(7, moved.getInt("New", "Old", 5));
	}

	@Test
	void int_legacyTuned_moduleTuned_module() {
		module.set("New", 9);
		legacy.set("Old", 7);
		assertEquals(9, moved.getInt("New", "Old", 5));
	}

	@Test
	void int_legacyEqualsDefault_module() {
		module.set("New", 9);
		legacy.set("Old", 5);
		assertEquals(9, moved.getInt("New", "Old", 5));
	}

	@Test
	void legacyFileMissing_module() {
		FileManager none = mock(FileManager.class);
		MovedSetting noLegacy = MovedSetting.of(mock(FileHandler.class), none, "turf");
		assertEquals(5, noLegacy.getInt("New", "Old", 5));
	}

	// ── boolean ─────────────────────────────────────────────────────────────

	@Test
	void boolean_nothingSet_default() {
		assertTrue(moved.getBoolean("New", "Old", true));
	}

	@Test
	void boolean_legacyTuned_moduleAbsent_legacy() {
		legacy.set("Old", false);
		assertFalse(moved.getBoolean("New", "Old", true));
	}

	@Test
	void boolean_legacyTuned_moduleSetToDefault_legacy() {
		module.set("New", true);
		legacy.set("Old", false);
		assertFalse(moved.getBoolean("New", "Old", true));
	}

	@Test
	void boolean_legacyEqualsDefault_moduleTuned_module() {
		module.set("New", false);
		legacy.set("Old", true);
		assertFalse(moved.getBoolean("New", "Old", true));
	}

	@Test
	void boolean_bothTuned_module() {
		module.set("New", true);
		legacy.set("Old", true);
		assertTrue(moved.getBoolean("New", "Old", false));
	}

	// ── money ───────────────────────────────────────────────────────────────

	@Test
	void money_nothingSet_default() {
		assertEquals(0, new BigDecimal("100").compareTo(moved.getMoney("New", "Old", "100")));
	}

	@Test
	void money_legacyTuned_moduleAbsent_legacy() {
		legacy.set("Old", "250.50");
		assertEquals(0, new BigDecimal("250.50").compareTo(moved.getMoney("New", "Old", "100")));
	}

	@Test
	void money_legacyNumericallyEqualToDefault_module() {
		module.set("New", 300);
		legacy.set("Old", "100.00");
		assertEquals(0, new BigDecimal("300").compareTo(moved.getMoney("New", "Old", "100")));
	}

	@Test
	void money_moduleSetToDefaultScale_legacyTuned_legacy() {
		module.set("New", "100.0");
		legacy.set("Old", 42);
		assertEquals(0, new BigDecimal("42").compareTo(moved.getMoney("New", "Old", "100")));
	}

	@Test
	void money_bothTuned_module() {
		module.set("New", 300);
		legacy.set("Old", 42);
		assertEquals(0, new BigDecimal("300").compareTo(moved.getMoney("New", "Old", "100")));
	}

	@Test
	void money_malformedModuleValue_countsAsUnset() {
		module.set("New", "lots");
		legacy.set("Old", 42);
		assertEquals(0, new BigDecimal("42").compareTo(moved.getMoney("New", "Old", "100")));
	}

	// ── String list ─────────────────────────────────────────────────────────

	@Test
	void list_nothingSet_default() {
		assertEquals(List.of("a"), moved.getStringList("New", "Old", List.of("a")));
	}

	@Test
	void list_legacyTuned_moduleAbsent_legacy() {
		legacy.set("Old", List.of("x", "y"));
		assertEquals(List.of("x", "y"), moved.getStringList("New", "Old", List.of("a")));
	}

	@Test
	void list_legacyTuned_moduleSetToDefault_legacy() {
		module.set("New", List.of("a"));
		legacy.set("Old", List.of("x"));
		assertEquals(List.of("x"), moved.getStringList("New", "Old", List.of("a")));
	}

	@Test
	void list_bothTuned_module() {
		module.set("New", List.of("m"));
		legacy.set("Old", List.of("x"));
		assertEquals(List.of("m"), moved.getStringList("New", "Old", List.of("a")));
	}

	@Test
	void list_legacyEqualsDefault_module() {
		module.set("New", List.of("m"));
		legacy.set("Old", List.of("a"));
		assertEquals(List.of("m"), moved.getStringList("New", "Old", List.of("a")));
	}

	// ── other types, one tuned-legacy case each ─────────────────────────────

	@Test
	void string_long_double_followTheSameRule() {
		legacy.set("S", "old");
		legacy.set("L", 99L);
		legacy.set("D", 2.5);
		module.set("D2", 7.5);
		legacy.set("D2", 2.5);

		assertEquals("old", moved.getString("S", "S", "def"));
		assertEquals(99L, moved.getLong("L", "L", 1L));
		assertEquals(2.5, moved.getDouble("D", "D", 1.0));
		assertEquals(7.5, moved.getDouble("D2", "D2", 1.0));
	}

	// ── Keystone backup (upgrade: settings.yml regenerated without the moved keys) ─────────────────────────────

	@Test
	void backup_usedWhenSettingsLacksKey() throws IOException {
		backup("settings-old.yml", "Old: 7\n", 1_000);
		assertEquals(7, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_settingsWinsOverBackup() throws IOException {
		backup("settings-old.yml", "Old: 7\n", 1_000);
		legacy.set("Old", 8);
		assertEquals(8, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_absent_noLegacy() {
		assertEquals(5, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_moduleTuned_module() throws IOException {
		backup("settings-old.yml", "Old: 7\n", 1_000);
		module.set("New", 9);
		assertEquals(9, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_newestNumberedBackupWins() throws IOException {
		backup("settings-old.yml", "Old: 7\n", 1_000);
		backup("settings-old (1).yml", "Old: 9\n", 3_000);
		backup("settings-old (2).yml", "Old: 8\n", 2_000);
		assertEquals(9, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_malformed_ignored() throws IOException {
		backup("settings-old.yml", "Old: [unclosed\n", 1_000);
		assertEquals(5, moved.getInt("New", "Old", 5));
	}

	@Test
	void backup_otherFilesNotMistakenForBackup() throws IOException {
		backup("settings-older.yml", "Old: 7\n", 1_000);
		backup("settings-old.yml.bak", "Old: 7\n", 1_000);
		assertEquals(5, moved.getInt("New", "Old", 5));
	}

	private void backup(String name, String yaml, long modified) throws IOException {
		File file = Files.writeString(dataFolder.resolve(name), yaml, StandardCharsets.UTF_8).toFile();
		assertTrue(file.setLastModified(modified));
	}
}
