package org.luckyraven.gangland.data.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link GangMembership} — the core "is/which gang" fact holder (R9, WS5 G1 step 9b) that lets
 * {@code gadget}/{@code civilians}/{@code cops-n-crooks} read gang facts without a {@code Depends: [gang]} edge.
 * Inert-until-installed behaviour matters as much as the installed behaviour: every consumer (e.g.
 * {@code GangAllyWeaponImpactListener}, {@code GanglandCarGangs}, {@code UserDataLoader}) must degrade safely on
 * a server that never loads the gang module at all.
 */
@DisplayName("GangMembership - inert-until-installed holder")
class GangMembershipTest {

	private final UUID alice = UUID.randomUUID();
	private final UUID bob   = UUID.randomUUID();

	@Test
	@DisplayName("gangIdOf returns -1 before any view is installed (module absent)")
	void gangIdOf_noViewInstalled_returnsAbsentSentinel() {
		GangMembership membership = new GangMembership();

		assertEquals(-1, membership.gangIdOf(alice));
	}

	@Test
	@DisplayName("gangIdOf(null) is -1 even with a view installed")
	void gangIdOf_nullUuid_returnsAbsentSentinel() {
		GangMembership membership = new GangMembership();
		membership.install(fixedView(alice, 7));

		assertEquals(-1, membership.gangIdOf(null));
	}

	@Test
	@DisplayName("gangIdOf delegates to the installed view once one exists")
	void gangIdOf_delegatesToInstalledView() {
		GangMembership membership = new GangMembership();
		membership.install(fixedView(alice, 7));

		assertEquals(7, membership.gangIdOf(alice));
		assertEquals(-1, membership.gangIdOf(bob), "an uuid the view doesn't know is still -1, not a NPE");
	}

	@Test
	@DisplayName("gangsAllied is false before any view is installed")
	void gangsAllied_noViewInstalled_isFalse() {
		GangMembership membership = new GangMembership();

		assertFalse(membership.gangsAllied(1, 2));
	}

	@Test
	@DisplayName("alliedOrSame: same non-absent gang id is true, even without a view (pure id comparison)")
	void alliedOrSame_sameGang_isTrueRegardlessOfView() {
		GangMembership membership = new GangMembership();
		membership.install(fixedView(alice, 5));
		membership.install(view(uuid -> uuid.equals(alice) || uuid.equals(bob) ? 5 : -1, (a, b) -> false,
		                       id -> Optional.empty()));

		assertTrue(membership.alliedOrSame(alice, bob), "both resolve to gang 5 - same gang, not allied");
	}

	@Test
	@DisplayName("alliedOrSame: two gang-less uuids (-1 == -1) must not read as \"same gang\"")
	void alliedOrSame_bothGangless_isFalse() {
		GangMembership membership = new GangMembership();
		membership.install(view(uuid -> -1, (a, b) -> false, id -> Optional.empty()));

		assertFalse(membership.alliedOrSame(alice, bob),
		            "the idA != -1 guard must exclude the both-absent false positive");
	}

	@Test
	@DisplayName("alliedOrSame: different but allied gangs is true via gangsAllied")
	void alliedOrSame_alliedGangs_isTrue() {
		GangMembership membership = new GangMembership();
		membership.install(view(uuid -> uuid.equals(alice) ? 1 : 2,
		                       (a, b) -> (a == 1 && b == 2) || (a == 2 && b == 1), id -> Optional.empty()));

		assertTrue(membership.alliedOrSame(alice, bob));
	}

	@Test
	@DisplayName("alliedOrSame: different, non-allied gangs is false")
	void alliedOrSame_unrelatedGangs_isFalse() {
		GangMembership membership = new GangMembership();
		membership.install(view(uuid -> uuid.equals(alice) ? 1 : 2, (a, b) -> false, id -> Optional.empty()));

		assertFalse(membership.alliedOrSame(alice, bob));
	}

	@Test
	@DisplayName("nameOf returns empty before any view is installed (module absent)")
	void nameOf_noViewInstalled_returnsEmpty() {
		GangMembership membership = new GangMembership();

		assertTrue(membership.nameOf(7).isEmpty());
	}

	@Test
	@DisplayName("nameOf delegates to the installed view once one exists")
	void nameOf_delegatesToInstalledView() {
		GangMembership membership = new GangMembership();
		membership.install(view(u -> -1, (a, b) -> false, id -> id == 7 ? Optional.of("The Syndicate") : Optional.empty()));

		assertEquals(Optional.of("The Syndicate"), membership.nameOf(7));
		assertTrue(membership.nameOf(8).isEmpty(), "an id the view doesn't know is still empty, not a NPE");
	}

	private GangMembershipView fixedView(UUID uuid, int gangId) {
		return view(u -> u.equals(uuid) ? gangId : -1, (a, b) -> false, id -> Optional.empty());
	}

	private GangMembershipView view(java.util.function.Function<UUID, Integer> gangIdOf,
	                                java.util.function.BiPredicate<Integer, Integer> allied,
	                                java.util.function.Function<Integer, Optional<String>> nameOf) {
		return new GangMembershipView() {
			@Override
			public int gangIdOf(UUID uuid) {
				return gangIdOf.apply(uuid);
			}

			@Override
			public boolean gangsAllied(int gangIdA, int gangIdB) {
				return allied.test(gangIdA, gangIdB);
			}

			@Override
			public Optional<String> nameOf(int gangId) {
				return nameOf.apply(gangId);
			}
		};
	}

}
