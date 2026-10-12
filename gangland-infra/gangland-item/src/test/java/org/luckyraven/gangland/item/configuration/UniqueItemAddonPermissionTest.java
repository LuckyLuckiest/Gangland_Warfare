package org.luckyraven.gangland.item.configuration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UniqueItemAddon registers gangland.uniqueitem.<id> with PermissionDefault.TRUE (T-825)")
class UniqueItemAddonPermissionTest {

	@BeforeAll
	static void bukkit() {
		BukkitRegistryFixture.install();
		when(Bukkit.getServer().getVersion()).thenReturn("git-Spigot-abcdef (MC: 1.21.11)");
	}

	@Test
	void uniqueItemNode_isOpen() throws Exception {
		PermissionManager manager = mock(PermissionManager.class);
		when(manager.resolve(anyString())).thenAnswer(call -> call.getArgument(0));
		FileManager       files   = mock(FileManager.class);
		FileHandler       handler = mock(FileHandler.class);
		when(files.getFile("unique_items")).thenReturn(handler);
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString("""
				badge:
				   Material: PAPER
				   Name: "&bBadge"
				   Inventory:
				      Slot: -1
				""");
		when(handler.getFileConfiguration()).thenReturn(config);
		PluginManager plugins = mock(PluginManager.class);
		when(Bukkit.getServer().getPluginManager()).thenReturn(plugins);

		UniqueItemAddon addon = new UniqueItemAddon(manager, files, mock(FuelService.class), null);
		addon.initialize();

		String expected = addon.getUniqueItem("badge").getPermission();
		ArgumentCaptor<Permission> added = ArgumentCaptor.forClass(Permission.class);
		verify(plugins).addPermission(added.capture());
		assertEquals(expected, added.getValue().getName());
		assertEquals(PermissionDefault.TRUE, added.getValue().getDefault());
		verify(manager).addPermission(expected);
	}
}
