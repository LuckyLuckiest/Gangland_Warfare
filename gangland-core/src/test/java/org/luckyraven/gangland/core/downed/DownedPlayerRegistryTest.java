package org.luckyraven.gangland.core.downed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link DownedPlayerRegistry}'s static membership tracking (Component table,
 * wanted-bounty-combat.md: "DownedPlayerRegistry - Static ConcurrentHashMap.newKeySet() of downed
 * UUIDs"). Six other modules poll this registry to gate behaviour on a player being downed, so its
 * add/remove/contains contract is the whole surface worth pinning.
 *
 * <p><b>Unverified — module ownership.</b> {@code gangland-core} is owned by a different agent for
 * Maven runs in this initiative; this suite could not be compiled or executed and is reported as
 * an unverified draft.
 *
 * <p>The registry is process-wide static state, so every test clears its own UUID in
 * {@code @AfterEach} to avoid leaking membership into unrelated tests running later in the same
 * JVM fork.
 */
@DisplayName("DownedPlayerRegistry - static downed-membership tracking")
class DownedPlayerRegistryTest {

	private final UUID uuid = UUID.randomUUID();

	@AfterEach
	void tearDown() {
		DownedPlayerRegistry.remove(uuid);
	}

	@Test
	@DisplayName("an unregistered UUID is not downed")
	void isDowned_falseForUnknownUuid() {
		assertFalse(DownedPlayerRegistry.isDowned(uuid));
	}

	@Test
	@DisplayName("add() makes isDowned() report true for exactly that UUID")
	void add_marksUuidAsDowned() {
		DownedPlayerRegistry.add(uuid);

		assertTrue(DownedPlayerRegistry.isDowned(uuid));
		assertFalse(DownedPlayerRegistry.isDowned(UUID.randomUUID()), "membership must not leak to other UUIDs");
	}

	@Test
	@DisplayName("remove() clears membership")
	void remove_clearsMembership() {
		DownedPlayerRegistry.add(uuid);
		DownedPlayerRegistry.remove(uuid);

		assertFalse(DownedPlayerRegistry.isDowned(uuid));
	}

	@Test
	@DisplayName("add() is idempotent - adding the same UUID twice does not error and stays downed once removed once")
	void add_isIdempotent() {
		DownedPlayerRegistry.add(uuid);
		DownedPlayerRegistry.add(uuid);

		assertTrue(DownedPlayerRegistry.isDowned(uuid));

		DownedPlayerRegistry.remove(uuid);

		assertFalse(DownedPlayerRegistry.isDowned(uuid), "a single remove() clears a double add() (it's a Set, not a counter)");
	}

	@Test
	@DisplayName("remove() on a UUID that was never added is a safe no-op")
	void remove_unknownUuid_isNoop() {
		assertDoesNotThrow(() -> DownedPlayerRegistry.remove(UUID.randomUUID()));
	}

}
