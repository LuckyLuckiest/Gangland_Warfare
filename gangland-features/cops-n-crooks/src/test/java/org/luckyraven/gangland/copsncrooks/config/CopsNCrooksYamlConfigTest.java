package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CopsNCrooksYamlConfig - an admin's npc/ files move to copsncrooks/")
class CopsNCrooksYamlConfigTest {

	@TempDir
	Path dataFolder;

	@Test
	@DisplayName("ships every default from copsncrooks/ in the module jar")
	void shippedDefaultsLiveInModuleFolder() {
		for (String name : CopsNCrooksYamlConfig.FILES) {
			String path = CopsNCrooksYamlConfig.DIRECTORY + "/" + name + ".yml";
			assertTrue(getClass().getClassLoader().getResource(path) != null, path);
			assertTrue(getClass().getClassLoader().getResource("npc/" + name + ".yml") == null, "npc/" + name);
		}
	}

	@Test
	@DisplayName("moves npc/cops.yml with its contents when copsncrooks/cops.yml is absent")
	void movesLegacyFile() throws IOException {
		Path legacy = write("npc/cops.yml", "Tuned: true\n");

		assertTrue(CopsNCrooksYamlConfig.relocateLegacy(dataFolder.toFile(), "cops"));

		assertFalse(Files.exists(legacy));
		assertEquals("Tuned: true\n", Files.readString(dataFolder.resolve("copsncrooks/cops.yml")));
	}

	@Test
	@DisplayName("leaves both files alone when copsncrooks/cops.yml already exists")
	void keepsExistingTarget() throws IOException {
		Path legacy = write("npc/cops.yml", "Old: true\n");
		Path target = write("copsncrooks/cops.yml", "New: true\n");

		assertFalse(CopsNCrooksYamlConfig.relocateLegacy(dataFolder.toFile(), "cops"));

		assertEquals("Old: true\n", Files.readString(legacy));
		assertEquals("New: true\n", Files.readString(target));
	}

	@Test
	@DisplayName("does nothing on a fresh server")
	void noLegacyFile() {
		assertFalse(CopsNCrooksYamlConfig.relocateLegacy(dataFolder.toFile(), "cops"));
		assertFalse(Files.exists(dataFolder.resolve("copsncrooks")));
	}

	private Path write(String relative, String content) throws IOException {
		Path file = dataFolder.resolve(relative);
		Files.createDirectories(file.getParent());
		return Files.writeString(file, content);
	}
}
