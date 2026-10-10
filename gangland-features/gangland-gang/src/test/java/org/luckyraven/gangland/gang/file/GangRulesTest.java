package org.luckyraven.gangland.gang.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.GangLevels;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link GangRules}: defaults, lower-casing, the preset floor, a bad regex and an unknown rank. */
@DisplayName("GangRules - gang_rules.yml")
class GangRulesTest {

	@AfterEach
	void restoreLevelDefaults() {
		GangLevels.bind(20, 200);
	}

	@Test
	@DisplayName("the shipped Progression section gives max level 20 and base experience 200")
	void progressionDefaults() throws IOException {
		String shipped = new String(GangRulesTest.class.getResourceAsStream("/gang/gang_rules.yml").readAllBytes(),
		                            StandardCharsets.UTF_8);
		GangRules rules = load(shipped);

		assertEquals(20, rules.getMaxLevel());
		assertEquals(200.0, rules.getBaseExperience());
	}

	@Test
	@DisplayName("the Progression section binds the level curve that new gangs are built with")
	void progressionBindsGangLevels() throws IOException {
		load("Progression:\n   Max_Level: 5\n   Base_Experience: 50\n");

		Level level = GangLevels.newLevel();

		assertEquals(5, level.getMaxLevel());
		assertEquals(50.0, level.getBaseAmount());
		assertEquals(GangLevels.FORMULA, level.getFormula());
	}

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private GangRules load(String yaml) throws IOException {
		Path file = tempDir.resolve("gang_rules.yml");
		Files.writeString(file, yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangRules(fileManager);
	}

	@Test
	@DisplayName("an empty file gives the default name pattern and preset")
	void emptyFileDefaults() throws IOException {
		GangRules rules = load("Other: 1\n");

		assertTrue(rules.getNamePattern().matcher("Abc_123").matches());
		assertTrue(!rules.getNamePattern().matcher("ab").matches());
		assertEquals(List.of("member", "officer", "owner"), rules.getRankPreset());
	}

	@Test
	@DisplayName("the preset is trimmed, lower-cased and de-duplicated, keeping its order")
	void presetCleanedUp() throws IOException {
		GangRules rules = load("Ranks:\n   Preset:\n      - Member\n      - \" OFFICER \"\n      - member\n      - owner\n");

		assertEquals(List.of("member", "officer", "owner"), rules.getRankPreset());
	}

	@Test
	@DisplayName("a preset of fewer than two ranks falls back to the default")
	void shortPresetFallsBack() throws IOException {
		GangRules rules = load("Ranks:\n   Preset:\n      - solo\n");

		assertEquals(List.of("member", "officer", "owner"), rules.getRankPreset());
	}

	@Test
	@DisplayName("an invalid name regex falls back to the default pattern")
	void invalidRegexFallsBack() throws IOException {
		GangRules rules = load("Name_Pattern: \"[unclosed\"\n");

		assertTrue(rules.getNamePattern().matcher("Gang_07").matches());
		assertTrue(!rules.getNamePattern().matcher("x").matches());
	}

	@Test
	@DisplayName("a custom name regex is compiled as written")
	void customRegex() throws IOException {
		GangRules rules = load("Name_Pattern: \"[a-z]{2,4}\"\n");

		assertTrue(rules.getNamePattern().matcher("abcd").matches());
		assertTrue(!rules.getNamePattern().matcher("ABCD").matches());
	}

	@Test
	@DisplayName("a rank with no preset permissions gets an empty list")
	void missingRankIsEmpty() throws IOException {
		GangRules rules = load("Ranks:\n   Preset_Permissions:\n      member:\n         - gangland.gang.deposit\n");

		assertEquals(List.of(), rules.getPresetPermissions("owner"));
	}

	@Test
	@DisplayName("a rank's preset permissions are read by lower-cased name and trimmed")
	void permissionsTrimmed() throws IOException {
		GangRules rules = load("Ranks:\n   Preset_Permissions:\n      member:\n         - \" gangland.gang.deposit \"\n");

		assertEquals(List.of("gangland.gang.deposit"), rules.getPresetPermissions("Member"));
	}
}
