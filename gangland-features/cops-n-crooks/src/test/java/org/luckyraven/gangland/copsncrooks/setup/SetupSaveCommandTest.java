package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.gangland.data.region.PlaceRegion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code /glw cop setup save} and {@code remove}: every mode writes only through the C5 registries. */
@DisplayName("SetupCommands.save and remove")
class SetupSaveCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SetupFixture fx;
	private Player       admin;
	private SetupSelection selection;

	@BeforeEach
	void setUp() throws IOException {
		fx        = new SetupFixture(tempDir);
		admin     = fx.admin();
		selection = fx.selections.get(admin.getUniqueId());
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private void corners(SetupMode mode) {
		selection.setMode(mode);
		selection.set(fx.at(0, 60, 0), true);
		selection.set(fx.at(10, 70, 10), false);
	}

	@Test
	@DisplayName("a station save creates the station at pos1 and assigns the spawners within Station_Radius")
	void stationSave_createsAndAssignsNearbySpawners() {
		selection.setMode(SetupMode.STATION);
		selection.set(fx.at(100, 64, 200), true);
		when(fx.spawns.assignNearby(any(Station.class), anyDouble())).thenReturn(3);

		fx.commands.save(admin, "Central");

		Station station = fx.stations.byName("Central");
		assertNotNull(station);
		assertEquals(100.0, station.getX());
		assertEquals(200.0, station.getZ());
		verify(fx.spawns).assignNearby(station, DispatchSettings.DEFAULT.stationRadius());
		verify(admin).sendMessage(fx.text(SetupMessages.Key.STATION_SAVED,
		                                  Map.of("name", "Central", "id", "1", "spawners", "3")));
	}

	@Test
	@DisplayName("the radius is read from the loaded cop config on every save")
	void stationSave_readsStationRadiusPerCall() {
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getDispatchSettings()).thenReturn(new DispatchSettings(true, 10.0, 0, 40, 12.5, 15));
		fx.provider = () -> provider;
		selection.setMode(SetupMode.STATION);
		selection.set(fx.at(1, 64, 1), true);

		fx.commands.save(admin, "Annex");

		verify(fx.spawns).assignNearby(any(Station.class), eq(12.5));
	}

	@Test
	@DisplayName("a second station with the same name refuses and stores nothing")
	void duplicateStationName_refuses_storesNothing() {
		selection.setMode(SetupMode.STATION);
		selection.set(fx.at(1, 64, 1), true);
		fx.commands.save(admin, "Central");

		selection.set(fx.at(50, 64, 50), true);
		fx.commands.save(admin, "central");

		assertEquals(1, fx.stations.all().size());
		verify(fx.spawns, times(1)).assignNearby(any(Station.class), anyDouble());
		verify(admin).sendMessage(fx.text(SetupMessages.Key.STATION_DUPLICATE, Map.of("name", "central")));
	}

	@Test
	@DisplayName("a district save creates a tagged region PlaceNames can name")
	void districtSave_createsTaggedRegion_readableThroughPlaceNames() {
		corners(SetupMode.DISTRICT);

		fx.commands.save(admin, "Downtown");

		PlaceNames names = new PlaceNames();
		names.register(fx.regions);
		Location inside = fx.at(5, 64, 5);
		assertEquals(Optional.of("Downtown"), names.locate(inside));
		assertTrue(names.withTag(inside, PlaceRegion.TAG_DISTRICT).isPresent());
		assertEquals(1, fx.regions.all().size());
	}

	@Test
	@DisplayName("hideout and restricted saves carry their own tag")
	void hideoutAndRestricted_carryTheirTags() {
		corners(SetupMode.HIDEOUT);
		fx.commands.save(admin, "Den");
		corners(SetupMode.RESTRICTED);
		fx.commands.save(admin, "Vault");

		assertEquals(java.util.Set.of("hideout"), fx.regions.get(1).getTags());
		assertEquals(java.util.Set.of("restricted"), fx.regions.get(2).getTags());
	}

	@Test
	@DisplayName("a pickup save stores a pickup point at pos1")
	void pickupSave_createsPickupPoint() {
		selection.setMode(SetupMode.PICKUP);
		selection.set(fx.at(7, 65, 9), true);

		fx.commands.save(admin, "Dock");

		SetupPoint point = fx.points.get(1);
		assertEquals(SetupPoint.PICKUP, point.getKind());
		assertEquals("Dock", point.getName());
		assertEquals(7.0, point.getX());
		assertEquals(9.0, point.getZ());
		assertTrue(fx.regions.all().isEmpty());
	}

	@Test
	@DisplayName("a breaker save stores the tagged region and a trigger point at the admin")
	void breakerSave_storesRegionAndTrigger() {
		corners(SetupMode.BREAKER);

		fx.commands.save(admin, "Bank Wall");

		assertEquals(java.util.Set.of("breaker"), fx.regions.get(1).getTags());
		SetupPoint trigger = fx.points.get(1);
		assertEquals(SetupPoint.BREAKER_TRIGGER, trigger.getKind());
		assertEquals(5.0, trigger.getX());
		assertEquals(64.0, trigger.getY());
		assertEquals(5.0, trigger.getZ());
	}

	@Test
	@DisplayName("an incomplete selection refuses and stores nothing, in every mode")
	void incompleteSelection_refuses() {
		selection.setMode(SetupMode.DISTRICT);
		selection.set(fx.at(0, 60, 0), true);

		fx.commands.save(admin, "Half");
		selection.setMode(SetupMode.STATION);
		fx.selections.clear(admin.getUniqueId());
		fx.commands.save(admin, "Nothing");

		assertTrue(fx.regions.all().isEmpty());
		assertTrue(fx.stations.all().isEmpty());
		assertTrue(fx.points.all().isEmpty());
		verify(fx.spawns, never()).assignNearby(any(Station.class), anyDouble());
		verify(admin).sendMessage(fx.text(SetupMessages.Key.INCOMPLETE, Map.of("needed", "pos1 and pos2", "mode", "district")));
		verify(admin).sendMessage(fx.text(SetupMessages.Key.INCOMPLETE, Map.of("needed", "pos1", "mode", "station")));
	}

	@Test
	@DisplayName("a blank name refuses")
	void blankName_refuses() {
		corners(SetupMode.DISTRICT);

		fx.commands.save(admin, "   ");

		assertTrue(fx.regions.all().isEmpty());
		verify(admin).sendMessage(fx.text(SetupMessages.Key.NAME_MISSING));
	}

	@Test
	@DisplayName("removing a station frees its spawners and forgets it")
	void removeStation_unassigns() {
		fx.stations.create("Central", fx.at(1, 64, 1));

		fx.commands.remove(admin, "station", "1");

		assertNull(fx.stations.get(1));
		verify(fx.spawns).unassignStation(1);
		verify(admin).sendMessage(fx.text(SetupMessages.Key.REMOVED, Map.of("kind", "station", "id", "1")));
	}

	@Test
	@DisplayName("removing an unknown id says so and touches nothing")
	void removeUnknown_refuses() {
		fx.commands.remove(admin, "region", "9");

		verify(admin).sendMessage(fx.text(SetupMessages.Key.NOT_FOUND, Map.of("kind", "region", "id", "9")));
		verify(fx.spawns, never()).unassignStation(org.mockito.ArgumentMatchers.anyInt());
	}
}
