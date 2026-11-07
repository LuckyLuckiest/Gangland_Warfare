package org.luckyraven.gangland.turf;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TurfModuleFileConfig#turfModuleFiles}: the six module YAMLs are registered with the FileManager one after
 * another, in the order the record lists them, each through the module classloader, each with {@code create = true}.
 */
@DisplayName("TurfModuleFileConfig - the six turf YAMLs register in order through the module classloader")
class TurfModuleFileConfigTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("each file is registered once, in record order, and the record hands back those same handlers")
	void registersSixFilesInOrder() {
		JavaPlugin        plugin      = PluginMocks.plugin(tempDir);
		FileManager       fileManager = mock(FileManager.class);
		ModuleLoader      loader      = mock(ModuleLoader.class);
		ModuleClassLoader classLoader = mock(ModuleClassLoader.class);
		when(loader.classLoader()).thenReturn(classLoader);

		TurfModuleFiles files = new TurfModuleFileConfig(plugin).turfModuleFiles(fileManager, loader);

		ArgumentCaptor<FileHandler> registered = ArgumentCaptor.forClass(FileHandler.class);
		InOrder                     order      = inOrder(fileManager);
		order.verify(fileManager, times(6)).addFile(registered.capture(), eq(true));
		verifyNoMoreInteractions(fileManager);

		List<FileHandler> handlers = registered.getAllValues();
		assertEquals(List.of("turf_powerups", "turf_npcs", "turf_settings", "turf_rules", "turf_map", "turf_messages"),
		             handlers.stream().map(FileHandler::getName).toList());
		for (FileHandler handler : handlers) {
			assertEquals("yml", handler.getFileType());
			assertSame(classLoader, handler.getResourceLoader());
		}
		assertSame(handlers.get(0), files.powerups());
		assertSame(handlers.get(1), files.npcs());
		assertSame(handlers.get(2), files.settings());
		assertSame(handlers.get(3), files.rules());
		assertSame(handlers.get(4), files.map());
		assertSame(handlers.get(5), files.messages());
	}
}
