package org.luckyraven.gangland;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.teleportation.WaypointLookupContract;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * WS6 G1: {@link GanglandApiImpl} is entirely new this gate (final ruling R7, {@code plans/WS6-api.md} §0c — the
 * facade's four accessors), so "red by non-existence" is the W48-accepted evidence: this test class could not
 * compile before {@link GanglandApiImpl} existed. Asserts (a) every accessor returns exactly the
 * constructor-injected instance and (b) {@link #gangs()} — the one accessor R7 changed from an
 * {@code Optional<GangLookupContract>} lazy container lookup to a direct, never-null {@link GangMembership}
 * reference — stays inert (every query answers its documented absent-default) until the gang module installs a
 * view, proving the facade doesn't paper over that with a different default.
 */
@DisplayName("GanglandApiImpl — the four accessors return exactly what the constructor was given")
class GanglandApiImplTest {

	@Test
	@DisplayName("users()/waypoints()/bankTiers()/gangs() return exactly the constructor-injected instances")
	void accessors_returnConstructorInjectedInstances() {
		UserLookupContract     users     = mock(UserLookupContract.class);
		GangMembership         gangs     = new GangMembership();
		WaypointLookupContract waypoints = mock(WaypointLookupContract.class);
		BankTiers              bankTiers = mock(BankTiers.class);

		GanglandApiImpl api = new GanglandApiImpl(users, gangs, waypoints, bankTiers);

		assertSame(users, api.users());
		assertSame(gangs, api.gangs());
		assertSame(waypoints, api.waypoints());
		assertSame(bankTiers, api.bankTiers());
	}

	@Test
	@DisplayName("gangs() is never null and stays inert (module-absent defaults) until GangMembership.install(...)")
	void gangs_neverNullAndInertUntilInstalled() {
		GanglandApiImpl api = new GanglandApiImpl(mock(UserLookupContract.class), new GangMembership(),
		                                          mock(WaypointLookupContract.class), mock(BankTiers.class));

		GangMembership gangs = api.gangs();

		assertFalse(gangs.isInstalled());
		assertEquals(-1, gangs.gangIdOf(UUID.randomUUID()));
		assertFalse(gangs.gangsAllied(1, 2));
		assertFalse(gangs.alliedOrSame(UUID.randomUUID(), UUID.randomUUID()));
	}

}
