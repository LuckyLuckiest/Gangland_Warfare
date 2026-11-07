package org.luckyraven.gangland.lootchest.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.module.ModuleClassLoader;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the KERNEL-phase registration of the loot chest module's five YAML defaults: which {@link FileHandler} is built
 * for which file (constructor arguments, in order), that each one is added to the {@link FileManager} as it is built,
 * and that the module class loader is asked for once per handler.
 */
@DisplayName("LootChestFileConfig - lootChestFiles")
class LootChestFileConfigTest {

	private static final List<String> NAMES = List.of("loot_chests", "tiers", "lootchest_messages",
	                                                  "lootchest_messages_es", "loot_chest_settings");

	@Test
	@DisplayName("registers the five lootchests/*.yml defaults in order, each built from the module class loader")
	void lootChestFiles_registersFiveHandlersInOrder() {
		JavaPlugin    plugin       = mock(JavaPlugin.class);
		FileManager   fileManager  = mock(FileManager.class);
		ModuleLoader  moduleLoader = mock(ModuleLoader.class);
		ModuleClassLoader classLoader = mock(ModuleClassLoader.class);
		List<List<?>> arguments    = new ArrayList<>();
		when(moduleLoader.classLoader()).thenReturn(classLoader);

		try (MockedConstruction<FileHandler> handlers = mockConstruction(FileHandler.class, (handler, context) -> {
			arguments.add(new ArrayList<>(context.arguments()));
		})) {
			LootChestFileConfig.LootChestFiles marker = new LootChestFileConfig(plugin).lootChestFiles(fileManager,
			                                                                                           moduleLoader);

			assertNotNull(marker);
			assertEquals(5, handlers.constructed().size());
			for (int i = 0; i < NAMES.size(); i++) {
				assertEquals(List.of(plugin, NAMES.get(i), "lootchests", ".yml", classLoader), arguments.get(i),
				             "constructor arguments of handler #" + i);
			}

			InOrder order = inOrder(fileManager);
			for (FileHandler handler : handlers.constructed()) {
				order.verify(fileManager).addFile(handler, true);
			}
			verifyNoMoreInteractions(fileManager);
			verify(moduleLoader, times(5)).classLoader();
		}
	}
}
