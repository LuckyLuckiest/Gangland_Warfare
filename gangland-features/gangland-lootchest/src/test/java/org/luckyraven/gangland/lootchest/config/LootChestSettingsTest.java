package org.luckyraven.gangland.lootchest.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drives a real {@link LootChestSettings#initialize()} pass off a hand-written
 * {@code lootchests/loot_chest_settings.yml} fixture — the same {@code FileHandler}/{@code FileManager} entry
 * point {@code LootChestFileConfig} uses at bootstrap (mirrors {@code SettingsFixture}'s approach for the core
 * {@code Settings} class). WS3 G4: the module's own settings provider no longer reads {@code gangland-api}'s
 * {@code Settings} statics.
 */
@DisplayName("LootChestSettings")
class LootChestSettingsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("a full loot_chest_settings.yml populates every getter, including the 5 reward getters (C4)")
	void initialize_populatesFromYaml() throws IOException {
		LootChestSettings settings = build("""
				Countdown_Timer: 60
				Sound:
				  Opening: BLOCK_BARREL_OPEN
				  Locked: BLOCK_CHEST_LOCKED
				  Closing: BLOCK_BARREL_CLOSE
				Allowed_Blocks:
				  - CHEST
				  - BARREL
				Rewards:
				  Money:
				    Minimum: 1
				    Maximum: 2
				  Experience:
				    Minimum: 3
				    Maximum: 4
				  Commands:
				    - "give %player% diamond 1"
				""");

		assertEquals(60L, settings.getCountdownTimer());
		assertEquals("BLOCK_BARREL_OPEN", settings.getOpeningSound());
		assertEquals("BLOCK_CHEST_LOCKED", settings.getLockedSound());
		assertEquals("BLOCK_BARREL_CLOSE", settings.getClosingSound());
		assertEquals(List.of("CHEST", "BARREL"), settings.getAllowedBlocks());
		assertEquals(1.0, settings.getRewardMoneyMinimum());
		assertEquals(2.0, settings.getRewardMoneyMaximum());
		assertEquals(3.0, settings.getRewardExperienceMinimum());
		assertEquals(4.0, settings.getRewardExperienceMaximum());
		assertEquals(List.of("give %player% diamond 1"), settings.getRewardCommands());
	}

	@Test
	@DisplayName("an empty loot_chest_settings.yml falls back to the same defaults the core Settings.java used")
	void initialize_emptyYaml_fallsBackToDefaults() throws IOException {
		LootChestSettings settings = build("{}\n");

		assertEquals(300L, settings.getCountdownTimer());
		assertEquals("BLOCK_CHEST_OPEN", settings.getOpeningSound());
		assertEquals("BLOCK_CHEST_LOCKED", settings.getLockedSound());
		assertEquals("BLOCK_CHEST_CLOSE", settings.getClosingSound());
		assertEquals(10.0, settings.getRewardMoneyMinimum());
		assertEquals(1_000.0, settings.getRewardMoneyMaximum());
		assertEquals(5.0, settings.getRewardExperienceMinimum());
		assertEquals(100.0, settings.getRewardExperienceMaximum());
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
