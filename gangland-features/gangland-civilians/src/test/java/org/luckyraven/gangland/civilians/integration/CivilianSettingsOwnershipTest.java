package org.luckyraven.gangland.civilians.integration;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.15.1 settings ownership: the keys that left {@code settings.yml} ({@code Civilians.*}, {@code NPC_Navigation.*})
 * are read from the module's own {@code npc/civilians.yml}, with a customised legacy value still honoured while the
 * module file holds the default.
 */
@DisplayName("Civilians settings ownership (npc/civilians.yml)")
class CivilianSettingsOwnershipTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("shipped civilians.yml defaults equal the old Settings code defaults")
	void shippedDefaults() throws IOException {
		FileManager fm = fileManager(shippedYaml(), null);

		GanglandCivilianSettings     settings = new GanglandCivilianSettings(fm);
		GanglandCivilianSpawnConfigProvider spawn = new GanglandCivilianSpawnConfigProvider(fm);
		settings.initialize();
		spawn.initialize();

		assertTrue(settings.isCivilianAiEnabled());
		assertEquals(20, settings.getCivilianAiTickRate());
		assertEquals(10, settings.getNavigationRecalculationTicks());
		assertEquals(5, settings.getStuckCheckIntervalTicks());
		assertEquals(3, settings.getMaxStuckChecks());
		assertEquals(6, settings.getMaxHopelessStuckChecks());
		assertEquals(8.0, settings.getHopelessCloseThreshold());
		assertEquals(0.75, settings.getMinProgressDistance());
		assertEquals(7.0, settings.getRangedMinDistance());
		assertEquals(12.0, settings.getRangedMaxDistance());
		assertEquals(2, settings.getMinRepathAfterLossTicks());
		assertEquals(60.0, settings.getCivilianSpawnerActivationRadius());
		assertEquals(80.0, settings.getCivilianSpawnerDespawnRadius());
		assertEquals(5, settings.getCivilianSpawnerMaxNpcs());
		assertEquals(30.0, settings.getCivilianSpawnerSoftLeashRadius());
		assertEquals(50.0, settings.getCivilianSpawnerHardLeashRadius());
		assertEquals(100, settings.getCivilianSpawnerCheckInterval());
		assertEquals("", settings.getCivilianSpawnerDefaultTypeId());

		assertEquals(10.0, spawn.getMinSpawnDistance());
		assertEquals(50.0, spawn.getMaxSpawnDistance());
		assertEquals(30.0, spawn.getPhase1MinDistance());
		assertEquals(5.0, spawn.getSpawnRadiusShrinkStep());
		assertEquals(10, spawn.getVerticalSearchRange());
		assertEquals(0, spawn.getSpawnYOffset());
		assertEquals(4.0, spawn.getMaxSpawnYDiff());
		assertEquals(16.0, spawn.getSpawnerMaxYDiff());
		assertEquals(2, spawn.getMinOpenHorizontalSides());
		assertEquals(80.0, spawn.getSpawnerPreferenceRadius());
		assertEquals(48.0, spawn.getVisibilityCheckDistance());
		assertEquals(20, spawn.getSpawnPhase1Attempts());
		assertEquals(15, spawn.getSpawnPhase2Attempts());
	}

	@Test
	@DisplayName("a customised settings.yml value wins while civilians.yml still holds the default")
	void legacyValueWins() throws IOException {
		String legacy = """
				NPC_Navigation:
				   Stuck_Check_Interval: 9
				Civilians:
				   Behaviour:
				      AI_Tick_Rate: 40
				   Spawn:
				      Max_Distance: 70.0
				   Spawner_Proximity:
				      Default_Type_Id: "pedestrian"
				""";
		FileManager fm = fileManager(shippedYaml(), legacy);

		GanglandCivilianSettings            settings = new GanglandCivilianSettings(fm);
		GanglandCivilianSpawnConfigProvider spawn    = new GanglandCivilianSpawnConfigProvider(fm);
		settings.initialize();
		spawn.initialize();

		assertEquals(9, settings.getStuckCheckIntervalTicks());
		assertEquals(40, settings.getCivilianAiTickRate());
		assertEquals("pedestrian", settings.getCivilianSpawnerDefaultTypeId());
		assertEquals(70.0, spawn.getMaxSpawnDistance());
		// untouched keys keep the module default
		assertEquals(3, settings.getMaxStuckChecks());
	}

	@Test
	@DisplayName("a customised civilians.yml value wins over settings.yml")
	void moduleValueWins() throws IOException {
		String module = shippedYaml().replace("Stuck_Check_Interval: 5", "Stuck_Check_Interval: 11")
		                             .replace("Max_Distance: 50.0", "Max_Distance: 65.0");
		String legacy = """
				NPC_Navigation:
				   Stuck_Check_Interval: 9
				Civilians:
				   Spawn:
				      Max_Distance: 70.0
				""";
		FileManager fm = fileManager(module, legacy);

		GanglandCivilianSettings            settings = new GanglandCivilianSettings(fm);
		GanglandCivilianSpawnConfigProvider spawn    = new GanglandCivilianSpawnConfigProvider(fm);
		settings.initialize();
		spawn.initialize();

		assertEquals(11, settings.getStuckCheckIntervalTicks());
		assertEquals(65.0, spawn.getMaxSpawnDistance());
	}

	private static String shippedYaml() throws IOException {
		try (InputStream in = CivilianSettingsOwnershipTest.class.getClassLoader()
		                                                         .getResourceAsStream("npc/civilians.yml")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private FileManager fileManager(String civiliansYaml, String settingsYaml) throws IOException {
		JavaPlugin  plugin = PluginMocks.plugin(tempDir);
		FileManager fm     = new FileManager(plugin);

		Files.writeString(tempDir.resolve("civilians.yml"), civiliansYaml, StandardCharsets.UTF_8);
		fm.addFile(new FileHandler(plugin, tempDir.resolve("civilians.yml").toFile()), false);

		if (settingsYaml != null) {
			Files.writeString(tempDir.resolve("settings.yml"), settingsYaml, StandardCharsets.UTF_8);
			fm.addFile(new FileHandler(plugin, tempDir.resolve("settings.yml").toFile()), false);
		}

		return fm;
	}
}
