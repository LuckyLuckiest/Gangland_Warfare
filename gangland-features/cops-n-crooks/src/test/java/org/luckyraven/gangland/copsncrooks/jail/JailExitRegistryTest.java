package org.luckyraven.gangland.copsncrooks.jail;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link JailExitRegistry}'s GLOBAL-vs-SPECIFIC exit bookkeeping: independent storage for the universal
 * fallback exit and per-jail exits, defensive cloning on every read/write so a caller can't mutate registry state
 * through a returned {@link Location}, and that {@link JailExitRegistry#snapshot()} — the repository data-supplier
 * feeding the {@code jail_exit} table's {@code row_id = -1} GLOBAL convention — only ever contains SPECIFIC rows
 * (the GLOBAL row is added separately by {@code JailExitService.snapshotAll}, covered in
 * {@link JailExitServiceTest}).
 */
@DisplayName("JailExitRegistry — GLOBAL vs SPECIFIC exit storage")
class JailExitRegistryTest {

	private JailExitRegistry registry;
	private World            world;

	@BeforeEach
	void setUp() {
		registry = new JailExitRegistry();
		world    = mock(World.class);
	}

	private Location loc(double x) {
		return new Location(world, x, 64, 0);
	}

	@Test
	@DisplayName("a jail with no configured exit resolves to null")
	void getExit_unconfigured_returnsNull() {
		assertNull(registry.getExit(1));
		assertFalse(registry.hasExit(1));
	}

	@Test
	@DisplayName("setExit/getExit round-trips a per-jail location")
	void setExit_thenGetExit_roundTrips() {
		registry.setExit(1, loc(10));

		Location result = registry.getExit(1);
		assertEquals(10, result.getX());
		assertTrue(registry.hasExit(1));
	}

	@Test
	@DisplayName("setExit with a null location clears the entry instead of storing null")
	void setExit_null_clearsEntry() {
		registry.setExit(1, loc(10));

		registry.setExit(1, null);

		assertNull(registry.getExit(1));
		assertFalse(registry.hasExit(1));
	}

	@Test
	@DisplayName("getExit returns a clone, not the stored instance — mutating the result can't corrupt the registry")
	void getExit_returnsDefensiveClone() {
		registry.setExit(1, loc(10));

		Location first  = registry.getExit(1);
		first.setX(999);
		Location second = registry.getExit(1);

		assertEquals(10, second.getX(), "the registry's stored location must be unaffected by mutating a prior read");
	}

	@Test
	@DisplayName("global exit is independent of per-jail exits")
	void globalExit_isIndependent() {
		registry.setExit(1, loc(10));
		registry.setGlobalExit(loc(500));

		assertEquals(500, registry.getGlobalExit().getX());
		assertEquals(10, registry.getExit(1).getX());
	}

	@Test
	@DisplayName("setGlobalExit(null) clears the global exit")
	void globalExit_setNull_clears() {
		registry.setGlobalExit(loc(500));

		registry.setGlobalExit(null);

		assertNull(registry.getGlobalExit());
	}

	@Test
	@DisplayName("clear(jailId) removes only that jail's exit, leaving others and the global exit untouched")
	void clear_removesOnlyNamedJail() {
		registry.setExit(1, loc(10));
		registry.setExit(2, loc(20));
		registry.setGlobalExit(loc(500));

		registry.clear(1);

		assertNull(registry.getExit(1));
		assertEquals(20, registry.getExit(2).getX());
		assertEquals(500, registry.getGlobalExit().getX());
	}

	@Test
	@DisplayName("snapshot contains only SPECIFIC (per-jail) rows, never the global exit")
	void snapshot_containsOnlySpecificRows() {
		registry.setExit(1, loc(10));
		registry.setExit(2, loc(20));
		registry.setGlobalExit(loc(500));

		Collection<JailExit> snapshot = registry.snapshot();

		assertEquals(2, snapshot.size());
		assertTrue(snapshot.stream().allMatch(exit -> !exit.isGlobal()));
		assertTrue(snapshot.stream().anyMatch(exit -> exit.getJailId() == 1));
		assertTrue(snapshot.stream().anyMatch(exit -> exit.getJailId() == 2));
	}

}
