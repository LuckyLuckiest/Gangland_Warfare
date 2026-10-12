package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.module.ModuleClassLoader;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gang module's KERNEL file registration: both shipped defaults are on the module classpath under gang/, and
 * {@link GangModuleFileConfig#gangModuleFiles} registers gang_rules and gang_messages with the FileManager.
 */
@DisplayName("GangModuleFileConfig - gang_rules.yml and gang_messages.yml registration")
class GangModuleConfigTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("the shipped defaults are on the module classpath under gang/")
	void shippedDefaultsOnClasspath() {
		assertNotNull(GangModuleConfigTest.class.getResource("/gang/gang_rules.yml"));
		assertNotNull(GangModuleConfigTest.class.getResource("/gang/gang_messages.yml"));
	}

	@Test
	@DisplayName("gangModuleFiles registers gang_rules and gang_messages, in that order, and returns both handlers")
	void registersBothFiles() {
		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		ModuleLoader loader = mock(ModuleLoader.class);
		when(loader.classLoader()).thenReturn(mock(ModuleClassLoader.class));
		FileManager fileManager = mock(FileManager.class);

		GangModuleFiles files = new GangModuleFileConfig(plugin).gangModuleFiles(fileManager, loader);

		verify(fileManager, times(2)).addFile(any(FileHandler.class), eq(true));
		assertEquals("gang_rules", files.rules().getName());
		assertEquals("gang_messages", files.messages().getName());
	}
}
