package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link Gang#isAlly} / {@link Gang#addAlly} / {@link Gang#removeAlly} and the missing {@code hashCode}
 * override, per the gangs-ranks-mail.md Test Surface line "Gang.isAlly / addAlly / removeAlly symmetry and
 * getAllies() immutability; Gang.equals vs missing hashCode (Obs. #35)".
 *
 * <p>{@link Gang#isAlly} is symmetric (TF-24): an alliance listed on either side counts for both gangs. Each
 * {@code Gang} still mutates only its own {@code allies} set; {@code GangManager} drops both sides when an alliance
 * ends.
 */
@DisplayName("Gang - alliances and identity")
class GangAllianceTest {

	@BeforeEach
	void bindSettings() {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
	}

	@Test
	@DisplayName("addAlly(Gang) on one side makes the alliance visible from both sides (TF-24: isAlly is symmetric)")
	void addAlly_isAllyFromBothSides() {
		Gang a = new Gang(10);
		Gang b = new Gang(11);

		a.addAlly(b);

		assertTrue(a.isAlly(b), "A added B as an ally");
		assertTrue(b.isAlly(a), "B lists nothing, but isAlly checks both sides, so the alliance is mutual");
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
	@DisplayName("removeAlly drops only the calling side's entry; the alliance stays until the other side drops too")
	void removeAlly_oneSided_stillAllyUntilBothSidesDrop() {
		Gang a = new Gang(14);
		Gang b = new Gang(15);
		a.addAlly(b);
		b.addAlly(a);

		a.removeAlly(b);

		assertTrue(a.isAlly(b), "B still lists A, so the symmetric isAlly keeps them allied");
		assertTrue(b.isAlly(a));

		b.removeAlly(a);

		assertFalse(a.isAlly(b), "both sides dropped - no longer allied");
		assertFalse(b.isAlly(a));
	}

	@Test
	void getAllies_isUnmodifiable() {
		Gang a = new Gang(16);
		Gang b = new Gang(17);
		a.addAlly(b);

		assertThrows(UnsupportedOperationException.class, () -> a.getAllies().clear());
	}

	@Test
	@DisplayName("GR-35: equal gangs (same id) have equal hashCodes")
	void equalGangs_haveEqualHashCodes() {
		Gang first  = new Gang(42);
		Gang second = new Gang(42);

		assertEquals(first, second, "same id must be equal");
		assertEquals(first.hashCode(), second.hashCode(), "hashCode must follow the id, like equals");
	}

	@Test
	@DisplayName("GR-35: a HashSet finds an equal Gang held by a different instance")
	void hashSet_findsEqualGang() {
		Gang first  = new Gang(99);
		Gang second = new Gang(99);

		HashSet<Gang> set = new HashSet<>();
		set.add(first);

		assertTrue(first.equals(second), "equals() says they are the same gang");
		assertTrue(set.contains(second), "the hashCode is id-based, so the bucket lookup finds it");
	}

}
