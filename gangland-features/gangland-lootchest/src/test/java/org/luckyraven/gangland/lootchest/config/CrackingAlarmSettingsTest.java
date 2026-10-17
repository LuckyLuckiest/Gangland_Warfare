package org.luckyraven.gangland.lootchest.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P-CRIME-3 (0.20.0): {@code Cracking_Alarm} of {@code lootchests/loot_chest_settings.yml} read through
 * {@link LootChestSettings#getCrackingAlarm()}, with code defaults when the block is missing and the shipped YAML
 * carrying the documented defaults.
 */
@DisplayName("LootChestSettings - Cracking_Alarm")
class CrackingAlarmSettingsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("a full Cracking_Alarm block is read, tier ids lowercased")
	void fullBlock() throws IOException {
		CrackingAlarmSettings alarm = build("""
				Cracking_Alarm:
				  Enable: false
				  Tiers:
				    - Vault_A
				    - military_vault
				  Sound: BLOCK_NOTE_BLOCK_BELL
				""").getCrackingAlarm();

		assertFalse(alarm.enabled());
		assertEquals(Set.of("vault_a", "military_vault"), alarm.tiers());
		assertEquals("BLOCK_NOTE_BLOCK_BELL", alarm.sound());
	}

	@Test
	@DisplayName("a file without the block falls back to enabled, the two state vault tiers and the bell")
	void missingBlock_defaults() throws IOException {
		CrackingAlarmSettings alarm = build("{}\n").getCrackingAlarm();

		assertTrue(alarm.enabled());
		assertEquals(Set.of("police_armory", "military_vault"), alarm.tiers());
		assertEquals("BLOCK_BELL_USE", alarm.sound());
	}

	@Test
	@DisplayName("a block with only Enable keeps the default tiers and sound")
	void partialBlock_keepsOtherDefaults() throws IOException {
		CrackingAlarmSettings alarm = build("""
				Cracking_Alarm:
				  Enable: true
				""").getCrackingAlarm();

		assertEquals(Set.of("police_armory", "military_vault"), alarm.tiers());
		assertEquals("BLOCK_BELL_USE", alarm.sound());
	}

	@Test
	@DisplayName("an explicitly empty Tiers list means no tier rings the alarm")
	void emptyTiers_meansNone() throws IOException {
		CrackingAlarmSettings alarm = build("""
				Cracking_Alarm:
				  Tiers: []
				""").getCrackingAlarm();

		assertEquals(Set.of(), alarm.tiers());
	}

	@Test
	@DisplayName("the DEFAULT constant equals the documented defaults")
	void defaultConstant() {
		assertTrue(CrackingAlarmSettings.DEFAULT.enabled());
		assertEquals(Set.of("police_armory", "military_vault"), CrackingAlarmSettings.DEFAULT.tiers());
		assertEquals("BLOCK_BELL_USE", CrackingAlarmSettings.DEFAULT.sound());
	}

	@Test
	@DisplayName("the shipped loot_chest_settings.yml documents Cracking_Alarm and loads to the defaults")
	void shippedYaml_carriesTheBlock() throws IOException {
		String yaml;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("lootchests/loot_chest_settings.yml")) {
			assertNotNull(in);
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		assertTrue(yaml.contains("Cracking_Alarm:"), "the block must be in the shipped file");
		assertTrue(yaml.contains("Tiers:") && yaml.contains("police_armory") && yaml.contains("military_vault"));
		assertTrue(yaml.contains("BLOCK_BELL_USE"));
		assertFalse(yaml.contains("{ "), "block-style YAML only");
		assertEquals(CrackingAlarmSettings.DEFAULT, build(yaml).getCrackingAlarm());
	}

	private LootChestSettings build(String yaml) throws IOException {
		Files.writeString(tempDir.resolve("loot_chest_settings.yml"), yaml, StandardCharsets.UTF_8);

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileHandler handler     = new FileHandler(plugin, tempDir.resolve("loot_chest_settings.yml").toFile());
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(handler, false);

		LootChestSettings settings = new LootChestSettings(fileManager);
		settings.initialize();
		return settings;
	}

}
