package org.luckyraven.gangland.copsncrooks.npc.police.config;

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

@DisplayName("CopLoader - the role catalogue comes from npc/cop_roles.yml, built-in when the file is not there")
class CopLoaderRolesFileTest {

	private static final String COPS = """
			Cops:
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			""";

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("with cop_roles.yml registered, its Squad_Composition and Roles are what the provider reads")
	void rolesFile_read() throws IOException {
		FileManager fileManager = files(COPS, """
				Roles:
				   Medic:
				      Health_Multiplier: 2.0
				Squad_Composition:
				   1:
				      - "Medic"
				""");

		CopConfigProvider provider = load(fileManager);

		assertEquals(List.of("Medic"), provider.getSquadComposition(1).stream().map(CopRole::name).toList());
		assertEquals(2.0, provider.getSquadComposition(1).get(0).healthMultiplier());
	}

	@Test
	@DisplayName("without cop_roles.yml the built-in catalogue and compositions apply")
	void noRolesFile_builtIns() throws IOException {
		CopConfigProvider provider = load(files(COPS, null));

		assertEquals(List.of("Pointman", "Assault"),
		             provider.getSquadComposition(1).stream().map(CopRole::name).toList());
	}

	private FileManager files(String cops, String roles) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		Files.writeString(tempDir.resolve("cops.yml"), cops, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("cops.yml").toFile()), false);
		if (roles != null) {
			Files.writeString(tempDir.resolve("cop_roles.yml"), roles, StandardCharsets.UTF_8);
			fileManager.addFile(new FileHandler(plugin, tempDir.resolve("cop_roles.yml").toFile()), false);
		}
		return fileManager;
	}

	private CopConfigProvider load(FileManager fileManager) {
		CopLoader loader = new CopLoader(PluginMocks.plugin(tempDir), null, null, false, null, fileManager);
		loader.loadData(null, fileManager);
		return loader.getLoadedProvider();
	}
}
