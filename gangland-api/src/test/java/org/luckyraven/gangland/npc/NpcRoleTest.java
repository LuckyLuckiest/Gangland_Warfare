package org.luckyraven.gangland.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NpcRole - neutral default, firing band clamp and front cone")
class NpcRoleTest {

	private static NpcRole role(Double min, Double max, double blockFraction, double cone) {
		return new NpcRole("R", "R", NpcFanPlacement.ANY, min, max, 1.0, 0, null, 1.0, 0, null, blockFraction, cone,
		                   false, false);
	}

	@Test
	void none_isNeutral() {
		NpcRole none = NpcRole.NONE;
		assertEquals(NpcFanPlacement.ANY, none.placement());
		assertEquals(1.0, none.healthMultiplier());
		assertEquals(1.0, none.fireRateScale());
		assertNull(none.rangedBand(30.0));
		assertFalse(none.blocks(0));
		assertFalse(none.medic());
		assertFalse(none.commander());
	}

	@Test
	void rangedBand_clampedUnderReach() {
		double[] band = role(20.0, 40.0, 0, 0).rangedBand(25.0);
		assertEquals(25.0, band[1]);
		assertTrue(band[1] - band[0] >= NpcRole.MIN_BAND_WIDTH);
		assertEquals(20.0, band[0]);
		assertArrayEquals(new double[]{20, 40}, role(20.0, 40.0, 0, 0).rangedBand(null));
	}

	@Test
	void rangedBand_keepsMinBelowMax() {
		double[] band = role(20.0, 40.0, 0, 0).rangedBand(21.0);
		assertEquals(21.0, band[1]);
		assertEquals(19.0, band[0]);
		assertEquals(0.0, role(20.0, 40.0, 0, 0).rangedBand(1.0)[0]);
	}

	@Test
	void blocks_onlyInsideTheFrontCone() {
		NpcRole shield = role(null, null, 0.5, 90);
		assertTrue(shield.blocks(30));
		assertTrue(shield.blocks(-45));
		assertFalse(shield.blocks(60));
		assertFalse(role(null, null, 0, 90).blocks(0));
	}
}
