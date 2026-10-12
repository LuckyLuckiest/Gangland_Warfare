package org.luckyraven.gangland.file.configuration.inventory;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-263: {@link InventoryLoader#refreshIfUnversioned} replaces an unversioned shipped menu from the jar once, keeps the
 * old file as {@code <name>-old.yml}, and leaves an owner menu (no jar copy) and a nested file alone.
 */
class InventoryLoaderRefreshTest {

	private static final String VERSION = "X";

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private Gangland        gangland;
	private InventoryLoader loader;

	@BeforeEach
	void setUp() throws IOException {
		gangland = mock(Gangland.class);
		when(gangland.getDataFolder()).thenReturn(tempDir.toFile());
		when(gangland.getDescription()).thenReturn(new PluginDescriptionFile("Gangland", VERSION, "main"));
		doAnswer(invocation -> {
			String path = invocation.getArgument(0);
			Path target = tempDir.resolve(path.replace('\\', '/'));
			Files.createDirectories(target.getParent());
			Files.writeString(target, shippedWithVersion(path));
			return null;
		}).when(gangland).saveResource(anyString(), anyBoolean());
		when(gangland.getResource(anyString())).thenAnswer(invocation -> {
			String path = invocation.getArgument(0);
			return path.equals("inventory/gang_info.yml") || path.equals("inventory/phone.yml")
			       ? new ByteArrayInputStream(shippedWithVersion(path).getBytes(StandardCharsets.UTF_8))
			       : null;
		});

		loader = new InventoryLoader(gangland, mock(FileManager.class), mock(InventoryRuntimeContext.class));
	}

	@Test
	void unversionedShippedMenu_isReplacedOnce_andOldFileKept() throws IOException {
		String shipped = shippedWithVersion("inventory/gang_info.yml");
		String oldText = shipped.substring(shipped.indexOf("Information:"));
		Path file = tempDir.resolve("inventory/gang_info.yml");
		Files.createDirectories(file.getParent());
		Files.writeString(file, oldText);

		loader.refreshIfUnversioned(new FileHandler(gangland, file.toFile()));

		assertEquals(oldText, Files.readString(tempDir.resolve("inventory/gang_info-old.yml")));
		assertEquals(shipped, Files.readString(file));

		FileHandler reloaded = new FileHandler(gangland, file.toFile());
		assertEquals(VERSION, reloaded.getFileConfiguration().getString("Config_Version"));
		assertNotNull(reloaded.getParsedDocument());
	}

	@Test
	void ownerMenuWithNoJarCopy_isLeftByteIdentical() throws IOException {
		String ownerText = "Information:\n  Name: 'Mine'\n";
		Path file = tempDir.resolve("inventory/my_menu.yml");
		Files.createDirectories(file.getParent());
		Files.writeString(file, ownerText);

		loader.refreshIfUnversioned(new FileHandler(gangland, file.toFile()));

		assertEquals(ownerText, Files.readString(file));
		assertFalse(Files.exists(tempDir.resolve("inventory/my_menu-old.yml")));
	}

	@Test
	void nestedFileWithSameBaseNameAsShippedMenu_isLeftAlone() throws IOException {
		String ownerText = "Information:\n  Name: 'Nested'\n";
		Path file = tempDir.resolve("inventory/custom/phone.yml");
		Files.createDirectories(file.getParent());
		Files.writeString(file, ownerText);

		loader.refreshIfUnversioned(new FileHandler(gangland, file.toFile()));

		assertEquals(ownerText, Files.readString(file));
		assertFalse(Files.exists(tempDir.resolve("inventory/custom/phone-old.yml")));
	}

	/** The shipped menu from the source tree with its {@code ${project.version}} placeholder filled in. */
	private static String shippedWithVersion(String resourcePath) throws IOException {
		Path source = Path.of("src/main/resources").resolve(resourcePath);
		return Files.readString(source, StandardCharsets.UTF_8).replace("${project.version}", VERSION);
	}
}
