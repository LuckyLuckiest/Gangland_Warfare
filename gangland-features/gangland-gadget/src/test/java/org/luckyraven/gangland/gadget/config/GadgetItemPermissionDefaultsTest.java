package org.luckyraven.gangland.gadget.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.keystone.module.ModuleClassLoader;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-825: the per-item nodes {@code gangland.cars.<id>}, {@code jetpacks.<id>} and {@code grapples.<id>} are created at
 * item load; they must be registered open to everyone, or a non-op cannot mount a bought car, equip a jetpack or fire
 * a grapple.
 */
@DisplayName("Gadget item addons register their per-item node with PermissionDefault.TRUE (T-825)")
class GadgetItemPermissionDefaultsTest {

	@BeforeAll
	static void bukkit() {
		BukkitRegistryFixture.install();
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");
	}

	private interface Wiring {

		void run(GadgetFileConfig config, PermissionManager manager, FileManager files, ModuleLoader loader);
	}

	private static void assertOpen(String fileName, String yaml, String node, Wiring wiring) throws Exception {
		PermissionManager manager = mock(PermissionManager.class);
		when(manager.resolve(node)).thenReturn(node);
		FileManager files   = mock(FileManager.class);
		FileHandler handler = mock(FileHandler.class);
		when(files.getFile(fileName)).thenReturn(handler);
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.loadFromString(yaml);
		} catch (InvalidConfigurationException exception) {
			throw new IllegalStateException(exception);
		}
		when(handler.getFileConfiguration()).thenReturn(config);
		ModuleLoader loader = mock(ModuleLoader.class);
		when(loader.classLoader()).thenReturn(mock(ModuleClassLoader.class));
		PluginManager plugins = mock(PluginManager.class);

		when(Bukkit.getServer().getPluginManager()).thenReturn(plugins);

		try (MockedConstruction<FileHandler> ignored = mockConstruction(FileHandler.class)) {

			wiring.run(new GadgetFileConfig(mock(JavaPlugin.class)), manager, files, loader);
		}

		ArgumentCaptor<Permission> added = ArgumentCaptor.forClass(Permission.class);
		verify(plugins).addPermission(added.capture());
		assertEquals(node, added.getValue().getName());
		assertEquals(PermissionDefault.TRUE, added.getValue().getDefault(), node + " must be open to everyone");
		verify(manager).addPermission(node);
	}

	@Test
	@DisplayName("gangland.cars.<id> is open")
	void car() throws Exception {
		assertOpen("cars", """
				sedan:
				   Material: MINECART
				   Display_Name: "&bSedan"
				""", "gangland.cars.sedan", (config, manager, files, loader) -> {
			CarAddon addon = config.carAddon(manager, files, null, loader);
			addon.initialize();
		});
	}

	@Test
	@DisplayName("gangland.jetpacks.<id> is open")
	void jetpack() throws Exception {
		assertOpen("jetpacks", """
				basic:
				   Material: IRON_CHESTPLATE
				   Name: "&bBasic"
				   Fuel_Key: "gasoline"
				""", "gangland.jetpacks.basic", (config, manager, files, loader) -> {
			JetpackAddon addon = config.jetpackAddon(manager, files, null, loader);
			addon.initialize();
		});
	}

	@Test
	@DisplayName("gangland.grapples.<id> is open")
	void grapple() throws Exception {
		assertOpen("grapples", """
				hook:
				   Material: FISHING_ROD
				   Display_Name: "&bHook"
				""", "gangland.grapples.hook", (config, manager, files, loader) -> {
			GrappleAddon addon = config.grappleAddon(manager, files, null, loader);
			addon.initialize();
		});
	}

	@Test
	@DisplayName("B2: the module must not use the host-new OpenPermissions (Host_Api 2.2 floor, no api bump)")
	void gadgetDoesNotNameOpenPermissions() throws Exception {
		String source = Files.readString(
				Path.of("src/main/java/org/luckyraven/gangland/gadget/config/GadgetFileConfig.java"));

		assertFalse(source.contains("OpenPermissions"), "register the TRUE node inside the module");
	}

	@Test
	@DisplayName("a car with the reserved id 'bypass' never opens the admin bypass node")
	void reservedBypassIdStaysAdmin() throws Exception {
		PermissionManager manager = mock(PermissionManager.class);
		when(manager.resolve("gangland.cars.bypass")).thenReturn("gangland.cars.bypass");
		FileManager files   = mock(FileManager.class);
		FileHandler handler = mock(FileHandler.class);
		when(files.getFile("cars")).thenReturn(handler);
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString("""
				bypass:
				   Material: MINECART
				   Display_Name: "&bSedan"
				""");
		when(handler.getFileConfiguration()).thenReturn(config);
		ModuleLoader loader = mock(ModuleLoader.class);
		when(loader.classLoader()).thenReturn(mock(ModuleClassLoader.class));
		PluginManager plugins = mock(PluginManager.class);
		when(Bukkit.getServer().getPluginManager()).thenReturn(plugins);

		try (MockedConstruction<FileHandler> ignored = mockConstruction(FileHandler.class)) {
			new GadgetFileConfig(mock(JavaPlugin.class)).carAddon(manager, files, null, loader).initialize();
		}

		verify(plugins, never()).addPermission(any(Permission.class));
	}
}
