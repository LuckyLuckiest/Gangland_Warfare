package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GP-38: a {@code Levels} row edited on disk is visible after {@link GangRules#reload()} (what /glw reload runs). */
@DisplayName("GangRules - reload")
class GangRulesReloadTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@AfterEach
	void restoreLevelDefaults() {
		GangLevels.bind(20, 200);
	}

	private GangRules load(String yaml) throws IOException {
		Path file = tempDir.resolve("gang_rules.yml");
		Files.writeString(file, yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangRules(fileManager);
	}

	@Test
	@DisplayName("levelCost_visibleAfterReload")
	void levelCost_visibleAfterReload() throws IOException {
		GangRules rules = load("Levels:\n   Level_1:\n      Cost: 20000\n      Max_Members: 10\n");
		assertEquals(20000D, rules.getLevelRows().get(1).cost());

		Files.writeString(tempDir.resolve("gang_rules.yml"),
		                  "Levels:\n   Level_1:\n      Cost: 30000\n      Max_Members: 12\n", StandardCharsets.UTF_8);
		rules.reload();

		assertEquals(30000D, rules.getLevelRows().get(1).cost());
		assertEquals(12, rules.getLevelRows().get(1).maxMembers());
	}

	@Test
	@DisplayName("missingSections_giveTheDocumentedDefaults")
	void missingSections_giveTheDocumentedDefaults() throws IOException {
		GangRules rules = load("Other: 1\n");

		assertTrue(rules.getLevelRows().isEmpty(), "a missing row is absent, W5-L inherits");
		assertEquals(24, rules.getRewardMinMemberAgeHours());
		assertEquals("officer", rules.getMinimumRanks().get("Kick"));
		assertEquals("owner", rules.getMinimumRanks().get("Upgrade"));
		assertTrue(rules.getDisabledWorlds().isEmpty());
		assertEquals("GANG_CHOICE", rules.getFriendlyFireGlobal());
		assertEquals(15, rules.getCombatTagSeconds());
		assertEquals(10, rules.getRankingPageSize());
		assertEquals("-", rules.getPlaceholdersDefaultValue());
	}

	@Test
	@DisplayName("worldNamesAreLowerCasedAndTrimmed")
	void worldNamesAreLowerCasedAndTrimmed() throws IOException {
		GangRules rules = load("Worlds:\n   Disabled:\n      - World_Nether \n      - ''\n");

		assertEquals(java.util.Set.of("world_nether"), rules.getDisabledWorlds());
	}
}
