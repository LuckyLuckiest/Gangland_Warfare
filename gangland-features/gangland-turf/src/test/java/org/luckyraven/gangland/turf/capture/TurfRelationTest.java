package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** {@link TurfRelation#of}: the five relations a viewer can have with a turf. */
@DisplayName("TurfRelation - how a viewer relates to a turf")
class TurfRelationTest {

	private static Turf turf(Integer ownerGangId) {
		return new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN, 0L, 0L);
	}

	@Test
	@DisplayName("an unclaimed turf is UNCLAIMED for everyone")
	void unclaimed() {
		assertEquals(TurfRelation.UNCLAIMED, TurfRelation.of(turf(null), 2, new FakeGangLookup()));
	}

	@Test
	@DisplayName("a viewer without a gang is GANGLESS")
	void gangless() {
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(CaptureFixtures.gang(1));

		assertEquals(TurfRelation.GANGLESS, TurfRelation.of(turf(1), null, gangs));
	}

	@Test
	@DisplayName("the owner's own members are OWN")
	void own() {
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(CaptureFixtures.gang(1));

		assertEquals(TurfRelation.OWN, TurfRelation.of(turf(1), 1, gangs));
	}

	@Test
	@DisplayName("an allied gang is ALLY, even when only one side lists the alliance")
	void allyOneSided() {
		FakeGangLookup gangs = new FakeGangLookup();
		Gang           owner = CaptureFixtures.gang(1);
		Gang           ally  = CaptureFixtures.gang(2);
		when(owner.isAlly(ally)).thenReturn(true);
		gangs.register(owner);
		gangs.register(ally);

		assertEquals(TurfRelation.ALLY, TurfRelation.of(turf(1), 2, gangs));
	}

	@Test
	@DisplayName("any other gang is RIVAL")
	void rival() {
		FakeGangLookup gangs = new FakeGangLookup();
		gangs.register(CaptureFixtures.gang(1));
		gangs.register(CaptureFixtures.gang(2));

		assertEquals(TurfRelation.RIVAL, TurfRelation.of(turf(1), 2, gangs));
	}
}
