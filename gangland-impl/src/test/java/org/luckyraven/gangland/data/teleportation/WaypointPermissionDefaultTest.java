package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.repositories.waypoint.WaypointRepository;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("T-826: waypoint use nodes default to true")
class WaypointPermissionDefaultTest {

	@AfterEach
	void tearDown() {
		Waypoint.setID(0);
	}

	@Test
	@DisplayName("loading a waypoint registers its use nodes (by id and by name) open to everyone")
	void initialize_registersWaypointNodesOpen() {
		PermissionManager manager = mock(PermissionManager.class);
		when(manager.resolve(anyString())).thenAnswer(call -> call.getArgument(0));
		GanglandDatabase database   = mock(GanglandDatabase.class);
		WaypointRepository repository = mock(WaypointRepository.class);
		RepositoryRegistry registry   = mock(RepositoryRegistry.class);
		when(database.getRepositoryRegistry()).thenReturn(registry);
		when(registry.getRepository(Waypoint.class)).thenReturn(repository);
		Waypoint waypoint = new Waypoint("spawn", "gangland");
		waypoint.setUsedId(2);
		waypoint.setType(Waypoint.WaypointType.SPAWN);
		waypoint.setCoordinates("world", 1, 2, 3, 0F, 0F);
		when(repository.loadAll()).thenReturn(List.of(waypoint));
		PluginManager plugins = mock(PluginManager.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

			new WaypointManager(mock(Gangland.class), database, manager).initialize();
		}

		ArgumentCaptor<Permission> added = ArgumentCaptor.forClass(Permission.class);
		verify(plugins, atLeastOnce()).addPermission(added.capture());
		List<String> names = added.getAllValues().stream().map(Permission::getName).toList();
		assertTrue(names.contains("gangland.waypoint.spawn"), names.toString());
		assertTrue(names.contains("waypoint.2"), names.toString());
		added.getAllValues().forEach(permission -> assertEquals(PermissionDefault.TRUE, permission.getDefault(),
		                                                       permission.getName()));
	}
}
