package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Garrison YAML - the shipped keys load into TurfDefenderConfig")
class GarrisonYamlTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path dir;

	@Test
	@DisplayName("the shipped turf_npcs.yml carries every Garrison key at its documented default")
	void shippedDefaults() throws IOException {
		TurfNpcsConfigLoader loader = new TurfNpcsConfigLoader(
				YamlFixture.fileManager(dir, "turf_npcs", YamlFixture.shipped("/turf/turf_npcs.yml")));

		TurfDefenderConfig config = loader.getDefenderConfig();
		assertEquals(3, config.waveSize());
		assertEquals(6, config.maxAlive());
		assertEquals(20, config.waveCooldownSeconds());
		assertEquals(8, config.standDownSeconds());
		assertEquals(16, config.leashMargin());
		assertEquals(0.5, config.bountyShare());
		assertEquals(12, config.recycleSeconds());
		assertEquals("turf_defender", config.typeId());
		assertEquals(6.0, config.holdRadius());
		assertEquals(60, loader.getOwnerAlertCooldownSeconds());
	}

	@Test
	@DisplayName("a file with no Garrison section keeps the defaults, and set keys override them")
	void missingSection_andOverrides() throws IOException {
		TurfNpcsConfigLoader loader = new TurfNpcsConfigLoader(YamlFixture.fileManager(dir, "turf_npcs",
				"Powerup_Npc:\n   Type_Id: quartermaster\nDefender:\n   Type_Id: turf_defender\n"
				+ "Garrison:\n   Wave_Size: 5\n"));

		assertEquals(5, loader.getDefenderConfig().waveSize());
		assertEquals(6, loader.getDefenderConfig().maxAlive());
		assertEquals(16, loader.getDefenderConfig().leashMargin());
		assertEquals(6.0, loader.getDefenderConfig().holdRadius());
	}

	@Test
	@DisplayName("turf_messages.yml carries the Garrison owner lines")
	void messageKeys() throws IOException {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
				new StringReader(YamlFixture.shipped("/turf/turf_messages.yml")));

		assertTrue(yaml.isSet("Garrison.Wave"));
		assertTrue(yaml.isSet("Garrison.Returned"));
	}
}
