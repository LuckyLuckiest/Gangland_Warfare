package org.luckyraven.gangland.core.permission;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.permission.PermissionManager;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpenPermissions - runtime nodes that default to true (T-825, T-826)")
class OpenPermissionsTest {

	@Test
	@DisplayName("registers the canonical node with PermissionDefault.TRUE before the manager tracks it")
	void register_opensNodeToEveryone() {
		PermissionManager manager = mock(PermissionManager.class);
		PluginManager     plugins = mock(PluginManager.class);
		when(manager.resolve("waypoint.4")).thenReturn("gangland.waypoint.4");
		// new Permission(...) recalculates through the server's plugin manager
		BukkitRegistryFixture.install();
		when(Bukkit.getServer().getPluginManager()).thenReturn(plugins);

		OpenPermissions.register(manager, plugins, "waypoint.4");

		ArgumentCaptor<Permission> added = ArgumentCaptor.forClass(Permission.class);
		var order = inOrder(plugins, manager);
		order.verify(plugins).addPermission(added.capture());
		order.verify(manager).addPermission("waypoint.4");
		assertEquals("gangland.waypoint.4", added.getValue().getName());
		assertEquals(PermissionDefault.TRUE, added.getValue().getDefault());
	}

	@Test
	@DisplayName("a node Bukkit already knows is left alone (Bukkit throws on duplicates)")
	void register_knownNodeNotReAdded() {
		PermissionManager manager = mock(PermissionManager.class);
		PluginManager     plugins = mock(PluginManager.class);
		when(manager.resolve("x")).thenReturn("gangland.x");
		when(plugins.getPermission("gangland.x")).thenReturn(mock(Permission.class));

		OpenPermissions.register(manager, plugins, "x");

		verify(plugins, never()).addPermission(any(Permission.class));
		verify(manager).addPermission("x");
	}

	@Test
	@DisplayName("without a server (unit tests) it still tracks the node")
	void register_noServer_stillTracks() {
		PermissionManager manager = mock(PermissionManager.class);

		OpenPermissions.register(manager, null, "y");

		verify(manager).addPermission("y");
	}
}
