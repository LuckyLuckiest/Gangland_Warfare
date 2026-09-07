package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link Gang#isAlly} / {@link Gang#addAlly} / {@link Gang#removeAlly} and the missing {@code hashCode}
 * override, per the gangs-ranks-mail.md Test Surface line "Gang.isAlly / addAlly / removeAlly symmetry and
 * getAllies() immutability; Gang.equals vs missing hashCode (Obs. #35)".
 *
 * <p>Disproves the implicit assumption that {@code isAlly} is symmetric: {@code Gang} only ever mutates its own
 * {@code allies} set, so symmetry is entirely the caller's responsibility (as done by
 * {@code GangAllyAcceptCommand.doAccept}, which calls {@code addAlly} on both sides). A single {@code addAlly}
 * call is one-directional.
 */
@DisplayName("Gang - alliances and identity")
class GangAllianceTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
	}

	@Test
	@DisplayName("addAlly(Gang) is one-directional: only the calling gang's own allies set gains the entry")
	void addAlly_isOneDirectional() {
		Gang a = new Gang(10);
		Gang b = new Gang(11);

		a.addAlly(b);

		assertTrue(a.isAlly(b), "A added B as an ally");
		assertFalse(b.isAlly(a), "B's own allies set was never touched - isAlly is NOT automatically symmetric");
	}

	@Test
	@DisplayName("mutual alliance requires both sides to call addAlly, exactly as GangAllyAcceptCommand.doAccept does")
	void addAlly_bothDirections_makesIsAllySymmetric() {
		Gang a = new Gang(12);
		Gang b = new Gang(13);

		a.addAlly(b);
		b.addAlly(a);

		assertTrue(a.isAlly(b));
		assertTrue(b.isAlly(a));
	}

	@Test
	@DisplayName("removeAlly drops the alliance from the calling gang's own set only, matched by ally id")
	void removeAlly_dropsMatchingAllyId() {
		Gang a = new Gang(14);
		Gang b = new Gang(15);
		a.addAlly(b);
		b.addAlly(a);

		a.removeAlly(b);

		assertFalse(a.isAlly(b), "A no longer lists B");
		assertTrue(b.isAlly(a), "abandon on one side (Gang.removeAlly) does not touch the other gang's set - "
		                        + "matches GangAllyAbandonCommand needing to call removeAlly on both gangs itself");
	}

	@Test
	void getAllies_isUnmodifiable() {
		Gang a = new Gang(16);
		Gang b = new Gang(17);
		a.addAlly(b);

		assertThrows(UnsupportedOperationException.class, () -> a.getAllies().clear());
	}

	@Test
	@DisplayName("Observation #35 (gangs-ranks-mail.md): equals() is overridden on id, but hashCode() is not")
	void equals_overriddenById_hashCodeNotOverridden() {
		Gang first  = new Gang(42);
		Gang second = new Gang(42);

		assertEquals(first, second, "same id must be equal");
		// Object identity hashCode is used because Gang never overrides hashCode(); two distinct instances with
		// the same id will (in practice, always) differ, breaking the equals/hashCode contract.
		assertNotEquals(System.identityHashCode(first), System.identityHashCode(second));
		assertNotEquals(first.hashCode(), second.hashCode(),
				"hashCode falls back to Object's identity hash since Gang does not override it");
	}

	@Test
	@DisplayName("Observation #35 consequence: a HashSet cannot find an equal Gang by a different instance because hashCode is identity-based")
	void hashSet_missesEqualGang_becauseHashCodeIsIdentityBased() {
		Gang first  = new Gang(99);
		Gang second = new Gang(99);

		HashSet<Gang> set = new HashSet<>();
		set.add(first);

		assertTrue(first.equals(second), "equals() says they are the same gang");
		assertFalse(set.contains(second), "but the HashSet bucket lookup uses the identity hashCode, so it misses");
	}

}
