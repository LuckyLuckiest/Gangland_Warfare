package org.luckyraven.gangland.data.teleportation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.repositories.waypoint.WaypointRepository;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.database.Database;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Final fix round 1 (R35 follow-up): a waypoint row of a type this version does not know is skipped at load but stays
 * in the table. Its id must never be handed to a new waypoint, and {@link WaypointManager#refactorIds} must neither
 * delete it nor trip over it.
 */
@DisplayName("WaypointManager - ids of rows skipped at load")
class WaypointManagerIdsTest {

	private BukkitStatics    bukkit;
	private GanglandDatabase database;
	private WaypointManager  manager;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install();
		database = mock(GanglandDatabase.class);
		manager  = new WaypointManager(mock(Gangland.class), database, mock(PermissionManager.class));
	}

	@AfterEach
	void tearDown() {
		Waypoint.setID(0);
		bukkit.close();
	}

	private static Waypoint waypoint(String name, int id) {
		Waypoint waypoint = new Waypoint(name, "gangland");
		waypoint.setUsedId(id);
		waypoint.setType(Waypoint.WaypointType.SPAWN);
		waypoint.setCoordinates("world", 1, 2, 3, 0F, 0F);
		return waypoint;
	}

	@Test
	@DisplayName("initialize floors the next id on the highest stored row, a skipped one included")
	void initialize_floorsTheNextIdOnTheHighestStoredRow() {
		WaypointRepository repository = mock(WaypointRepository.class);
		RepositoryRegistry registry   = mock(RepositoryRegistry.class);
		when(database.getRepositoryRegistry()).thenReturn(registry);
		when(registry.getRepository(Waypoint.class)).thenReturn(repository);
		when(repository.loadAll()).thenReturn(List.of(waypoint("kept", 2)));
		when(repository.getHighestStoredId()).thenReturn(5);

		manager.initialize();

		assertEquals(6, new Waypoint("new", "gangland").getUsedId());
	}

	@Test
	@DisplayName("refactorIds keeps a row it did not load and renumbers the rest around its id")
	void refactorIds_keepsUnloadedRow_andRenumbersAroundIt() throws SQLException {
		Waypoint a = waypoint("a", 1);
		Waypoint c = waypoint("c", 4);
		manager.add(a);
		manager.add(c);
		Database db = mock(Database.class);
		when(database.getDatabase()).thenReturn(db);
		when(db.table(anyString())).thenReturn(db);
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1});
		rows.add(new Object[]{2}); // a type this version does not know: never loaded
		rows.add(new Object[]{4});
		when(db.selectAll(any(String[].class))).thenReturn(rows);

		manager.refactorIds();

		verify(db, never()).delete(eq(""), any(), anyInt());
		verify(db, never()).delete(eq("id"), eq(2), eq(Types.INTEGER));
		verify(db).delete("id", 4, Types.INTEGER);
		assertSame(a, manager.get(1));
		assertSame(c, manager.get(3), "renumbered into the gap after the kept row");
		assertEquals(3, c.getUsedId());
		assertEquals(4, new Waypoint("new", "gangland").getUsedId());
	}

	@Test
	@DisplayName("refactorIds never moves the next id below a waypoint created but not yet autosaved")
	void refactorIds_keepsTheNextIdAboveAnUnsavedWaypoint() throws SQLException {
		manager.add(waypoint("a", 1));
		manager.add(waypoint("c", 3));
		Waypoint unsaved = waypoint("w", 5);
		manager.add(unsaved);
		Database db = mock(Database.class);
		when(database.getDatabase()).thenReturn(db);
		when(db.table(anyString())).thenReturn(db);
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{1});
		rows.add(new Object[]{3});
		when(db.selectAll(any(String[].class))).thenReturn(rows);

		manager.refactorIds();

		assertSame(unsaved, manager.get(5));
		assertEquals(6, new Waypoint("new", "gangland").getUsedId());
	}
}
