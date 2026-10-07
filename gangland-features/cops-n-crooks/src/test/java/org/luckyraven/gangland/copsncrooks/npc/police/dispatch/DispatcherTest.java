package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CONTRACTS C8: a dispatched unit leaves the nearest station of the target's world, with an ETA of the horizontal
 * distance over {@code Unit_Speed} clamped to 0-40 s and rounded up; an active hand-off bias prefers a station ahead,
 * but only while its ETA is within {@code Bias_Seconds} of the nearest's. No station, another world or dispatch off is
 * the ring at once.
 */
@DisplayName("Dispatcher - station and ETA of a dispatched unit")
class DispatcherTest {

	private final List<Station>     stations = new ArrayList<>();
	private final CopConfigProvider provider = mock(CopConfigProvider.class);
	private       World             world;
	private       Player            target;
	private       Dispatcher        dispatcher;

	@BeforeEach
	void setUp() {
		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		target = mock(Player.class);
		when(target.getWorld()).thenReturn(world);
		when(target.getLocation()).thenReturn(new Location(world, 0, 64, 0));

		StationRegistry registry = mock(StationRegistry.class);
		when(registry.all()).thenAnswer(inv -> new ArrayList<>(stations));
		when(provider.getDispatchSettings()).thenReturn(DispatchSettings.DEFAULT);
		when(provider.getHandoffSettings()).thenReturn(HandoffSettings.DEFAULT);
		dispatcher = new Dispatcher(registry, () -> provider);
	}

	private Station station(int id, String name, String worldName, double x, double z) {
		Station station = new Station(id, name, worldName, x, 64, z, 0f, null);
		stations.add(station);
		return station;
	}

	/** A bias heading +X from the target, active until 10 s. */
	private SpawnBias eastBias() {
		return new SpawnBias(new Vector(1, 0, 0), new Location(world, 0, 64, 0), 10_000L, 60.0);
	}

	@Test
	@DisplayName("the nearest station of the target's world wins, ETA = distance / 10 blocks per second")
	void nearestStation_wins() {
		Station near = station(1, "Northside", "world", 100, 0);
		station(2, "Docks", "world", 250, 0);

		Dispatcher.Plan plan = dispatcher.plan(target, 0L, null);

		assertSame(near, plan.station());
		assertEquals(10_000L, plan.etaMs());
	}

	@Test
	@DisplayName("an active bias prefers a station ahead whose ETA is within Bias_Seconds of the nearest's")
	void bias_prefersTheStationAhead() {
		station(1, "Behind", "world", -50, 0);                 // 5 s
		Station ahead = station(2, "Ahead", "world", 120, 0);  // 12 s <= 5 + 10

		Dispatcher.Plan plan = dispatcher.plan(target, 0L, eastBias());

		assertSame(ahead, plan.station());
		assertEquals(12_000L, plan.etaMs());
	}

	@Test
	@DisplayName("a station ahead beyond the nearest's ETA + Bias_Seconds loses to the nearest")
	void farStationAhead_losesToTheNearest() {
		Station behind = station(1, "Behind", "world", -50, 0); // 5 s
		station(2, "Far ahead", "world", 200, 0);                // 20 s > 15 s

		Dispatcher.Plan plan = dispatcher.plan(target, 0L, eastBias());

		assertSame(behind, plan.station());
		assertEquals(5_000L, plan.etaMs());
	}

	@Test
	@DisplayName("an expired bias is ignored: the nearest station wins")
	void expiredBias_isIgnored() {
		Station behind = station(1, "Behind", "world", -50, 0);
		station(2, "Ahead", "world", 120, 0);

		assertSame(behind, dispatcher.plan(target, 10_000L, eastBias()).station());
	}

	@Test
	@DisplayName("the ETA is horizontal, rounded up to a whole second and clamped to Max_Eta_Seconds")
	void eta_isHorizontal_roundedUp_andClamped() {
		Station station = station(1, "Tower", "world", 3, 0);
		assertEquals(1_000L, dispatcher.plan(target, 0L, null).etaMs(), "0.3 s rounds up to 1 s");

		stations.clear();
		stations.add(new Station(1, "Tower", "world", 0, 200, 95, 0f, null));
		assertEquals(10_000L, dispatcher.plan(target, 0L, null).etaMs(), "height is ignored: 9.5 s -> 10 s");

		stations.clear();
		stations.add(new Station(1, "Far", "world", 1_000, 64, 0, 0f, null));
		assertEquals(40_000L, dispatcher.plan(target, 0L, null).etaMs(), "clamped to 40 s");
		assertEquals("Tower", station.getName());
	}

	/** Coincident pass on the pre-change stub (which planned no station for everything); pins the world filter. */
	@Test
	@DisplayName("a station in another world is ignored: no station, the ring at once")
	void otherWorld_isIgnored() {
		station(1, "Nether Post", "world_nether", 10, 0);

		Dispatcher.Plan plan = dispatcher.plan(target, 0L, null);

		assertNull(plan.station());
		assertEquals(0L, plan.etaMs());
	}

	@Test
	@DisplayName("dispatch off (or no config) plans no station and no ETA")
	void disabled_plansNothing() {
		station(1, "Northside", "world", 100, 0);
		when(provider.getDispatchSettings()).thenReturn(DispatchSettings.DISABLED);

		assertEquals(new Dispatcher.Plan(null, 0L), dispatcher.plan(target, 0L, null));

		when(provider.getDispatchSettings()).thenReturn(null);
		assertEquals(10_000L, dispatcher.plan(target, 0L, null).etaMs(), "a null getter reads DEFAULT (enabled)");
	}
}
