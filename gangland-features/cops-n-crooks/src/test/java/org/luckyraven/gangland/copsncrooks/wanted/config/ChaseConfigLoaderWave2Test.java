package org.luckyraven.gangland.copsncrooks.wanted.config;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 0.21.0 fnd-w2: the loader exposes Getaway and Most_Wanted, defaults for an old file, and clears back to them. */
@DisplayName("ChaseConfigLoader - Getaway and Most_Wanted")
class ChaseConfigLoaderWave2Test {

	@TempDir
	Path tempDir;

	private ChaseConfigLoader loader(String yaml) throws IOException {
		Files.writeString(tempDir.resolve("wanted.yml"), yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("wanted.yml").toFile()), false);
		ChaseConfigLoader loader = new ChaseConfigLoader(plugin, fileManager);
		loader.loadData(null, fileManager);
		return loader;
	}

	@Test
	@DisplayName("a loaded file's blocks reach getGetaway() / getMostWanted(); clear() restores the defaults")
	void loadedBlocks_areExposed_andClear() throws IOException {
		ChaseConfigLoader loader = loader("Wanted:\n   Getaway:\n      Daily_Cap: 50\n"
		                                  + "   Most_Wanted:\n      Min_Stars: 6\n");

		assertEquals(50, loader.getGetaway().dailyCap());
		assertEquals(6, loader.getMostWanted().minStars());

		loader.clear();

		assertEquals(GetawaySettings.DEFAULT, loader.getGetaway());
		assertEquals(MostWantedSettings.DEFAULT, loader.getMostWanted());
	}
}
