package org.luckyraven.gangland.config;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.file.configuration.inventory.InventoryLoader;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.FolderLoader;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises the default inventory files {@code GameplayConfig.inventoryLoader} registers: twelve distinct
 * {@code inventory/<name>.yml} handlers, in this order (the order they are recreated in when absent).
 */
@DisplayName("GameplayConfig.inventoryLoader - expected default files")
class GameplayConfigInventoryLoaderTest {

	private static final List<String> NAMES = List.of(
			"alliance_stat", "gang_info", "gang_stat", "gang_ranking", "player_profile", "phone", "phone_banking",
			"phone_bounty", "phone_contacts", "phone_gang", "phone_gang_search", "user_stat");

	@TempDir
	Path tempDir;

	@SuppressWarnings("unchecked")
	private static List<FileHandler> expectedFiles(InventoryLoader loader) throws ReflectiveOperationException {
		Field field = FolderLoader.class.getDeclaredField("expectedFolderFiles");
		field.setAccessible(true);
		return (List<FileHandler>) field.get(loader);
	}

	@Test
	@DisplayName("registers the twelve inventory defaults in order, each a fresh handler")
	void registersTwelveDefaultsInOrder() throws Exception {
		Gangland gangland = mock(Gangland.class);
		PluginDescriptionFile description = mock(PluginDescriptionFile.class);
		when(description.getVersion()).thenReturn("0.21.8");
		when(gangland.getDataFolder()).thenReturn(tempDir.toFile());
		when(gangland.getDescription()).thenReturn(description);

		GameplayConfig config = new GameplayConfig(gangland, mock(GanglandContext.class));
		InventoryLoader loader = config.inventoryLoader(mock(FileManager.class), mock(InventoryRuntimeContext.class));

		List<FileHandler> expected = new ArrayList<>();
		for (String name : NAMES) {
			expected.add(new FileHandler(gangland, name, "inventory", ".yml"));
		}

		List<FileHandler> actual = expectedFiles(loader);

		assertEquals(expected, actual);
		assertEquals(12, actual.size());
		for (int index = 0; index < actual.size(); index++) {
			assertNotSame(actual.get(index), actual.get((index + 1) % actual.size()));
		}
		assertEquals("inventory", loader.getFolderName());
	}
}
