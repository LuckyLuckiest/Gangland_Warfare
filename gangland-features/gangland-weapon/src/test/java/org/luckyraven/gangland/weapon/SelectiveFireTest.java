package org.luckyraven.gangland.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pure-logic coverage for {@link SelectiveFire}: string parsing and the restricted-cycle walk used by
 * {@code WeaponSelectiveFireChangeListener} (see weapons.md W14 — Selective-fire switching).
 *
 * <p>Also pins the second half of Observation #36 (weapons.md): {@link SelectiveFire#getType} maps any
 * unrecognised or missing string (including the literal {@code "null"} produced by reading an absent NBT tag)
 * to {@link SelectiveFire#AUTO}, never throws.
 */
@DisplayName("SelectiveFire — parsing and restricted-cycle walk")
class SelectiveFireTest {

	@Test
	@DisplayName("getType maps 'single'/'burst' case-insensitively, everything else falls back to AUTO")
	void getType_mapsKnownStrings() {
		assertSame(SelectiveFire.SINGLE, SelectiveFire.getType("single"));
		assertSame(SelectiveFire.SINGLE, SelectiveFire.getType("SINGLE"));
		assertSame(SelectiveFire.BURST, SelectiveFire.getType("burst"));
		assertSame(SelectiveFire.BURST, SelectiveFire.getType("Burst"));
		assertSame(SelectiveFire.AUTO, SelectiveFire.getType("auto"));
	}

	@Test
	@DisplayName("getType falls back to AUTO for unknown or corrupt NBT text, including the literal 'null'")
	void getType_unknownFallsBackToAuto() {
		assertSame(SelectiveFire.AUTO, SelectiveFire.getType("not-a-mode"));
		assertSame(SelectiveFire.AUTO, SelectiveFire.getType("null"));
		assertSame(SelectiveFire.AUTO, SelectiveFire.getType(""));
	}

	@Test
	@DisplayName("getNextState() (no restriction) cycles AUTO -> BURST -> SINGLE -> AUTO")
	void getNextState_unrestrictedCycle() {
		assertSame(SelectiveFire.BURST, SelectiveFire.AUTO.getNextState());
		assertSame(SelectiveFire.SINGLE, SelectiveFire.BURST.getNextState());
		assertSame(SelectiveFire.AUTO, SelectiveFire.SINGLE.getNextState());
	}

	@Test
	@DisplayName("getNextState(Set) with null or empty set behaves exactly like the unrestricted cycle")
	void getNextState_nullOrEmptySet_unrestricted() {
		assertSame(SelectiveFire.BURST, SelectiveFire.AUTO.getNextState(null));
		assertSame(SelectiveFire.BURST, SelectiveFire.AUTO.getNextState(Set.of()));
	}

	@Test
	@DisplayName("getNextState(Set) skips modes outside the allowed set, preserving AUTO->BURST->SINGLE order")
	void getNextState_restrictedSet_skipsDisallowed() {
		Set<SelectiveFire> allowed = Set.of(SelectiveFire.AUTO, SelectiveFire.SINGLE);

		// AUTO -> (BURST skipped, not allowed) -> SINGLE
		assertSame(SelectiveFire.SINGLE, SelectiveFire.AUTO.getNextState(allowed));
		// SINGLE -> (AUTO allowed)
		assertSame(SelectiveFire.AUTO, SelectiveFire.SINGLE.getNextState(allowed));
	}

	@Test
	@DisplayName("getNextState(Set) with a single-element set returns 'this' unchanged")
	void getNextState_singleElementSet_returnsSelf() {
		Set<SelectiveFire> onlySingle = Set.of(SelectiveFire.SINGLE);

		assertSame(SelectiveFire.SINGLE, SelectiveFire.SINGLE.getNextState(onlySingle));
	}

	@Test
	@DisplayName("getNextState(Set) with a set that does not contain the starting mode still returns 'this'")
	void getNextState_startingModeNotInSet_returnsSelf() {
		// AUTO is cycling but only BURST is allowed — AUTO itself is never re-offered by getNextState()'s walk
		// (it starts from the next state and stops after one full loop), so the method falls through to `this`.
		Set<SelectiveFire> onlyBurst = Set.of(SelectiveFire.BURST);

		assertSame(SelectiveFire.BURST, SelectiveFire.AUTO.getNextState(onlyBurst));
		// SINGLE cycling with only BURST allowed: SINGLE -> AUTO(skip) -> BURST(allowed)
		assertSame(SelectiveFire.BURST, SelectiveFire.SINGLE.getNextState(onlyBurst));
	}

	@Test
	@DisplayName("getState(int) indexes the fixed enum order")
	void getState_indexesEnumOrder() {
		assertEquals(SelectiveFire.AUTO, SelectiveFire.getState(0));
		assertEquals(SelectiveFire.BURST, SelectiveFire.getState(1));
		assertEquals(SelectiveFire.SINGLE, SelectiveFire.getState(2));
	}

}
