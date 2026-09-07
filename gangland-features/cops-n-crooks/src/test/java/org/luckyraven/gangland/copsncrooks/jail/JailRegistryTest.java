package org.luckyraven.gangland.copsncrooks.jail;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link JailRegistry} cell bookkeeping: add/remove/dedupe, occupant lookup, capacity via {@link Jail}, and
 * the {@code setJailLocation} "move an existing id instead of creating a new one" behaviour that underlies
 * cops-detainment-jail.md Observation #2. That observation is about {@code JailService.ID} (a mutable static
 * restored by plain assignment, not {@code max}) reusing a deleted/out-of-order id — this suite pins the
 * registry-level mechanics {@code JailService} relies on: calling {@link JailRegistry#setJailLocation} twice with the
 * same id relocates the existing {@link Jail} (and, implicitly, its inmates) rather than creating a second cell.
 */
@DisplayName("JailRegistry — cell CRUD, occupancy and id-collision mechanics")
class JailRegistryTest {

	private JailRegistry registry;
	private World         world;

	@BeforeEach
	void setUp() {
		registry = new JailRegistry();
		world    = mock(World.class);
	}

	private Location loc(double x, double y, double z) {
		return new Location(world, x, y, z);
	}

	@Test
	@DisplayName("setJailLocation with an unused id creates a new cell")
	void setJailLocation_newId_createsCell() {
		Jail jail = registry.setJailLocation(1, loc(0, 64, 0), 2);

		assertEquals(1, jail.getId());
		assertSame(jail, registry.getJail(1));
		assertEquals(1, registry.getCells().size());
	}

	@Test
	@DisplayName("setJailLocation with an existing id MOVES the cell instead of creating a second one (root of Observation #2)")
	void setJailLocation_reusedId_movesExistingCellRatherThanCreatingNew() {
		Jail first = registry.setJailLocation(1, loc(0, 64, 0), 2);
		first.addPlayer(UUID.randomUUID());

		Jail second = registry.setJailLocation(1, loc(100, 70, 100), 2);

		assertSame(first, second, "the SAME Jail instance is returned/relocated, not a new one");
		assertEquals(1, registry.getCells().size(), "no second cell was created");
		assertEquals(100, registry.getJailLocation(1).getX(), "the existing cell's location was overwritten");
		assertTrue(first.isJailOccupied(), "the relocated cell keeps its prior inmates — they silently move with it");
	}

	@Test
	@DisplayName("detainPlayer adds to the named cell and removes the player from every other cell first")
	void detainPlayer_movesPlayerBetweenCells() {
		registry.addJail(new Jail(1, loc(0, 64, 0), 2));
		registry.addJail(new Jail(2, loc(10, 64, 10), 2));
		UUID player = UUID.randomUUID();

		registry.detainPlayer(1, player);
		registry.detainPlayer(2, player);

		assertTrue(registry.getJail(1).getJailedPlayersId().isEmpty(), "player must be removed from the old cell");
		assertEquals(List.of(player), registry.getJail(2).getJailedPlayersId());
		assertEquals(2, registry.getJailIdForPlayer(player));
	}

	@Test
	@DisplayName("detainPlayer against an unknown jail id throws rather than silently dropping the player")
	void detainPlayer_unknownJailId_throws() {
		assertThrows(IllegalArgumentException.class, () -> registry.detainPlayer(99, UUID.randomUUID()));
	}

	@Test
	@DisplayName("releasePlayer clears the player from whichever cell holds them")
	void releasePlayer_clearsFromAnyCell() {
		registry.addJail(new Jail(1, loc(0, 64, 0), 2));
		UUID player = UUID.randomUUID();
		registry.detainPlayer(1, player);

		registry.releasePlayer(player);

		assertNull(registry.getJailIdForPlayer(player));
		assertTrue(registry.getJail(1).getJailedPlayersId().isEmpty());
	}

	@Test
	@DisplayName("findAvailableJailId returns the first registered cell's id regardless of capacity")
	void findAvailableJailId_returnsFirstRegisteredCell() {
		registry.addJail(new Jail(5, loc(0, 64, 0), 1));
		registry.addJail(new Jail(6, loc(1, 64, 1), 1));

		assertEquals(5, registry.findAvailableJailId());
	}

	@Test
	@DisplayName("findAvailableJailId returns null when no jails are registered")
	void findAvailableJailId_noJails_returnsNull() {
		assertNull(registry.findAvailableJailId());
	}

	@Test
	@DisplayName("removeJail drops the cell but leaves any tracked occupants unresolved (caller's responsibility)")
	void removeJail_dropsCell() {
		registry.addJail(new Jail(1, loc(0, 64, 0), 2));

		Jail removed = registry.removeJail(1);

		assertEquals(1, removed.getId());
		assertNull(registry.getJail(1));
	}

	@Test
	@DisplayName("Jail enforces capacity is observable via isJailOccupied and addPlayer dedupes")
	void jail_capacityAndDedupe() {
		Jail jail = new Jail(1, loc(0, 64, 0), 1);
		UUID player = UUID.randomUUID();

		jail.addPlayer(player);
		jail.addPlayer(player); // duplicate add must not double-list the player

		assertEquals(1, jail.getJailedPlayersId().size());
		assertTrue(jail.isJailOccupied());
		assertEquals(1, jail.getMaxCapacity());
	}

	@Test
	@DisplayName("clear empties every cell")
	void clear_removesAllCells() {
		registry.addJail(new Jail(1, loc(0, 64, 0), 2));
		registry.addJail(new Jail(2, loc(1, 64, 1), 2));

		registry.clear();

		assertTrue(registry.getCells().isEmpty());
	}

}
