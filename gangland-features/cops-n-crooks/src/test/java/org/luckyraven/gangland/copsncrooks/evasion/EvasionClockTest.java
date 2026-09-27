package org.luckyraven.gangland.copsncrooks.evasion;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 0.12 F2: the per-player evasion clock. A fresh squad sighting keeps the player SEEN and resets the clock; losing sight
 * for {@code Lost_Sight_Seconds} opens a search zone on the last known position and counts toward
 * {@code Seconds_To_Drop}, twice as fast outside the zone by default.
 */
@DisplayName("EvasionClock")
class EvasionClockTest {

	private static final long SEEN_NOW = 0L;
	private static final long LOST     = 5_000L; // past the default 3 s Lost_Sight_Seconds

	private World         world;
	private EvasionConfig config;
	private EvasionClock  clock;

	@BeforeEach
	void setUp() {
		world  = mock(World.class);
		config = EvasionConfig.defaults();
		clock  = new EvasionClock();
	}

	@Test
	@DisplayName("a new clock is NONE and snapshots as none")
	void newClock_isNone() {
		assertEquals(EvasionState.NONE, clock.getState());
		assertEquals(EvasionSnapshot.none(), clock.snapshot(3, config));
	}

	@Test
	@DisplayName("a fresh sighting is SEEN, never due, and resets a running clock")
	void freshSighting_isSeenAndResets() {
		Location at = at(0, 0);

		clock.tick(LOST, at, at, 1, config, 1D);
		clock.tick(LOST, at, at, 1, config, 1D);
		assertEquals(2D, clock.getElapsedSeconds());

		boolean due = clock.tick(SEEN_NOW, at, at, 1, config, 1D);

		assertFalse(due);
		assertEquals(EvasionState.SEEN, clock.getState());
		assertEquals(0D, clock.getElapsedSeconds());
		assertNull(clock.snapshot(1, config).zoneCenter(), "no search zone while seen");
	}

	@Test
	@DisplayName("a sighting just under Lost_Sight_Seconds still counts as seen")
	void sightingInsideLostSightWindow_isSeen() {
		Location at = at(0, 0);

		clock.tick(2_999L, at, at, 1, config, 1D);

		assertEquals(EvasionState.SEEN, clock.getState());
	}

	@Test
	@DisplayName("SEARCHING inside the zone counts one second per second on the last known position")
	void searchingInsideZone_countsAtNormalSpeed() {
		Location lastKnown = at(0, 0);

		clock.tick(LOST, lastKnown, at(10, 10), 2, config, 1D);

		EvasionSnapshot snapshot = clock.snapshot(2, config);
		assertEquals(EvasionState.SEARCHING, snapshot.state());
		assertEquals(1D, clock.getElapsedSeconds());
		assertEquals(20D, snapshot.totalSeconds());
		assertEquals(19D, snapshot.remainingSeconds());
		assertEquals(lastKnown, snapshot.zoneCenter());
		assertEquals(60D, snapshot.zoneRadius());
	}

	@Test
	@DisplayName("outside the zone the clock runs at Outside_Zone_Speed")
	void searchingOutsideZone_countsFaster() {
		clock.tick(LOST, at(0, 0), at(41, 0), 1, config, 1D);

		assertEquals(2D, clock.getElapsedSeconds(), "41 blocks out of a 40-block zone runs at 2.0x");
	}

	@Test
	@DisplayName("distance is horizontal: height does not take the player out of the zone")
	void zoneIsHorizontal() {
		Location center = new Location(world, 0, 64, 0);
		Location above  = new Location(world, 30, 200, 0);

		clock.tick(LOST, center, above, 1, config, 1D);

		assertEquals(1D, clock.getElapsedSeconds());
	}

	@Test
	@DisplayName("another world counts as outside the zone")
	void otherWorld_isOutside() {
		Location elsewhere = new Location(mock(World.class), 0, 64, 0);

		clock.tick(LOST, at(0, 0), elsewhere, 1, config, 1D);

		assertEquals(2D, clock.getElapsedSeconds());
	}

	@Test
	@DisplayName("the drop is due once the clock reaches Seconds_To_Drop; afterDrop restarts it and keeps SEARCHING")
	void dropDue_thenAfterDropRestarts() {
		Location at = at(0, 0);

		for (int i = 1; i < 10; i++) {
			assertFalse(clock.tick(LOST, at, at, 1, config, 1D), "not due after " + i + " s");
		}
		assertTrue(clock.tick(LOST, at, at, 1, config, 1D), "due after 10 s at one star");

		clock.afterDrop();

		assertEquals(EvasionState.SEARCHING, clock.getState());
		assertEquals(0D, clock.getElapsedSeconds());
	}

	@Test
	@DisplayName("with no squad position the zone sits where the player was last seen")
	void nullLastKnown_fallsBackToLastSeen() {
		clock.tick(SEEN_NOW, null, at(5, 5), 1, config, 1D);

		clock.tick(LOST, null, at(20, 20), 1, config, 1D);

		assertEquals(at(5, 5), clock.snapshot(1, config).zoneCenter());
	}

	@Test
	@DisplayName("never seen and no squad position: the zone is pinned where the search started, it does not follow")
	void neverSeen_zonePinnedAtSearchStart() {
		clock.tick(LOST, null, at(7, 7), 1, config, 1D);
		clock.tick(LOST, null, at(100, 100), 1, config, 1D);

		assertEquals(at(7, 7), clock.snapshot(1, config).zoneCenter());
	}

	@Test
	@DisplayName("reset forgets the state")
	void reset_returnsToNone() {
		Location at = at(0, 0);
		clock.tick(LOST, at, at, 1, config, 1D);

		clock.reset();

		assertEquals(EvasionState.NONE, clock.getState());
		assertEquals(0D, clock.getElapsedSeconds());
	}

	private Location at(double x, double z) {
		return new Location(world, x, 64, z);
	}
}
