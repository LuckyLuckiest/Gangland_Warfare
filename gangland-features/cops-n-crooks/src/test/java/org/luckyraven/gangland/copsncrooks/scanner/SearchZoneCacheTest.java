package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lane scanmap: {@link SearchZoneCache} keeps the latest evasion event per player (P-MAP-1 circle data). */
@DisplayName("SearchZoneCache")
class SearchZoneCacheTest {

	private final SearchZoneCache cache  = new SearchZoneCache();
	private final World           world  = mock(World.class);
	private final Player          lucky  = player();
	private final Player          other  = player();

	private static Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

	private static WantedEvasionStateEvent event(Player player, EvasionState state, Location centre, double radius) {
		return new WantedEvasionStateEvent(player, state, 3, state == EvasionState.SEARCHING ? 20 : 0, centre, radius);
	}

	@Test
	@DisplayName("SEARCHING stores state, centre and radius")
	void searchingStoresZone() {
		cache.record(event(lucky, EvasionState.SEARCHING, new Location(world, 10, 64, -5), 30));

		SearchZoneCache.Zone zone = cache.get(lucky.getUniqueId());

		assertNotNull(zone);
		assertEquals(EvasionState.SEARCHING, zone.state());
		assertEquals(10, zone.centre().getX());
		assertEquals(-5, zone.centre().getZ());
		assertEquals(30, zone.radius());
	}

	@Test
	@DisplayName("a later event replaces the earlier one; OFF clears the zone")
	void keepsLatestStatePerPlayer() {
		cache.record(event(lucky, EvasionState.SEARCHING, new Location(world, 1, 64, 1), 30));
		cache.record(event(lucky, EvasionState.SEARCHING, new Location(world, 2, 64, 2), 25));
		assertEquals(25, cache.get(lucky.getUniqueId()).radius());

		cache.record(event(lucky, EvasionState.OFF, null, 0));

		assertNull(cache.get(lucky.getUniqueId()));
	}

	@Test
	@DisplayName("SEEN and EVADED are remembered with their state so the renderer can skip the circle")
	void seenAndEvadedKept() {
		cache.record(event(lucky, EvasionState.SEEN, null, 0));
		assertEquals(EvasionState.SEEN, cache.get(lucky.getUniqueId()).state());
		assertNull(cache.get(lucky.getUniqueId()).centre());

		cache.record(event(lucky, EvasionState.EVADED, new Location(world, 3, 64, 3), 12));
		assertEquals(EvasionState.EVADED, cache.get(lucky.getUniqueId()).state());
	}

	@Test
	@DisplayName("players do not share zones")
	void perPlayerIsolated() {
		cache.record(event(lucky, EvasionState.SEARCHING, new Location(world, 1, 64, 1), 30));

		assertNull(cache.get(other.getUniqueId()));
		cache.record(event(other, EvasionState.OFF, null, 0));
		assertNotNull(cache.get(lucky.getUniqueId()));
	}

	@Test
	@DisplayName("evict drops the entry; evicting an unknown player is harmless")
	void evictRemoves() {
		cache.record(event(lucky, EvasionState.SEARCHING, new Location(world, 1, 64, 1), 30));

		cache.evict(lucky.getUniqueId());
		cache.evict(UUID.randomUUID());

		assertNull(cache.get(lucky.getUniqueId()));
	}

	@Test
	@DisplayName("an unknown player has no zone")
	void unknownPlayerHasNoZone() {
		assertNull(cache.get(UUID.randomUUID()));
	}
}
