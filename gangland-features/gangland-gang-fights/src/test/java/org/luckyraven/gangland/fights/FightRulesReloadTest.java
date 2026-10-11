package org.luckyraven.gangland.fights;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GP-38: a key edited on disk is visible after {@link FightRules#reload()} (what /glw reload runs). */
@DisplayName("FightRules - reload and defaults")
class FightRulesReloadTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private FightRules load(String yaml) throws IOException {
		Path file = tempDir.resolve("fight_rules.yml");
		Files.writeString(file, yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new FightRules(fileManager);
	}

	@Test
	@DisplayName("changedKey_visibleAfterReload")
	void changedKey_visibleAfterReload() throws IOException {
		FightRules rules = load("Challenge:\n   Countdown_Seconds: 10\nGuards:\n   Blocked_Commands: []\n");
		assertEquals(10, rules.getCountdownSeconds());

		Files.writeString(tempDir.resolve("fight_rules.yml"),
		                  "Challenge:\n   Countdown_Seconds: 3\nGuards:\n   Blocked_Commands:\n      - home\n",
		                  StandardCharsets.UTF_8);
		rules.reload();

		assertEquals(3, rules.getCountdownSeconds());
		assertEquals(List.of("home"), rules.getBlockedCommands());
	}

	@Test
	@DisplayName("missingKeys_giveTheDocumentedDefaults")
	void missingKeys_giveTheDocumentedDefaults() throws IOException {
		FightRules rules = load("Other: 1\n");

		assertEquals(60, rules.getChallengeExpirySeconds());
		assertEquals(5, rules.getMaxTeamSize());
		assertTrue(rules.isRequireEqualTeams());
		assertEquals("MOST_ALIVE", rules.getTimeoutResult());
		assertEquals(100000D, rules.getMaxBet());
		assertEquals(1000D, rules.getRankedMinBet());
		assertEquals("ALLOWLIST", rules.getCommandBlockMode());
		assertTrue(rules.getAllowedCommands().contains("msg"));
		assertFalse(rules.isAllowVehicles());
		assertEquals(64, rules.getMinArenaVolume());
	}

	@Test
	@DisplayName("the shipped file reads back the documented values")
	void shippedFile_readsBack() {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, "fight_rules", "fight", ".yml",
		                                    FightRulesReloadTest.class.getClassLoader()), true);
		FightRules shipped = new FightRules(fileManager);

		assertEquals(60, shipped.getJoinWindowSeconds());
		assertEquals(50, shipped.getMaxBetPercentOfBank());
		assertEquals(3, shipped.getRankedPerPairPerDay());
		assertEquals(3, shipped.getMaxLeaveWarnings());
		assertEquals(10, shipped.getOutlineSeconds());
		assertEquals(List.of("glw gang fight", "gangland gang fight", "msg", "r", "tell"),
		             shipped.getAllowedCommands());
	}
}
