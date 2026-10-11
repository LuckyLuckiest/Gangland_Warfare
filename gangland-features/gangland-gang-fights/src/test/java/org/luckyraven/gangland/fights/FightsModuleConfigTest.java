package org.luckyraven.gangland.fights;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.module.ModuleClassLoader;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.ModuleRegistrations;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FightsModule - what it declares to the host")
class FightsModuleConfigTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("configure_declaresConfigsAndPackages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new FightsModule().configure(registrations);

		assertEquals(List.of(FightsModuleFileConfig.class, FightsModuleConfig.class, FightsArenaConfig.class,
		                     FightsFlowConfig.class, FightsEconomyConfig.class), registrations.configurations());
		assertEquals(List.of(FightsModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(FightsModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(FightsModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("filesRegistered")
	void filesRegistered() {
		JavaPlugin   plugin = PluginMocks.plugin(tempDir);
		FileManager  files  = new FileManager(plugin);
		ModuleLoader loader = mock(ModuleLoader.class);
		when(loader.classLoader()).thenReturn(new ModuleClassLoader(FightsModuleConfigTest.class.getClassLoader()));

		FightsModuleFiles registered = new FightsModuleFileConfig(plugin).fightsModuleFiles(files, loader);

		assertNotNull(registered.rules());
		assertNotNull(files.getFile("fight_rules"));
		assertNotNull(files.getFile("fight_messages"));
		assertNotNull(files.getFile("arenas"));
	}
}
