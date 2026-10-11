package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
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

/** GP-06: a Name_Pattern that would accept whitespace, @, / or ; is refused at load (default pattern + warning). */
@DisplayName("GangRules - Name_Pattern safety")
class GangRulesNamePatternTest {

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
	@DisplayName("whitespaceOrAtPattern_rejectedAtLoad: an unsafe pattern falls back to the default")
	void whitespaceOrAtPattern_rejectedAtLoad() throws IOException {
		assertEquals("[A-Za-z0-9_]{3,16}", load("Name_Pattern: \"[A-Za-z0-9_ ]{3,16}\"\n").getNamePattern().pattern());
		assertEquals("[A-Za-z0-9_]{3,16}", load("Name_Pattern: \".{3,16}\"\n").getNamePattern().pattern());
		assertEquals("\\p{L}{3,16}", load("Name_Pattern: \"\\\\p{L}{3,16}\"\n").getNamePattern().pattern());
	}

}
