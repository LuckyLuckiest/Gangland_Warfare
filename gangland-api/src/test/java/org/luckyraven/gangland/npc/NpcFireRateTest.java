package org.luckyraven.gangland.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NpcFireRate.scale - Fire_Rate_Multiplier to Keystone fire-rate scale, same pre-0.12 cadence on both paths")
class NpcFireRateTest {

	@Test
	@DisplayName("an SPI (Bartizan) weapon gets 1 / multiplier: 0.1 = one weapon tick per 10-tick AI tick, as in 0.11")
	void spiWeapon_inverseOfMultiplier() {
		NpcRangedAttack bartizan = mock(NpcRangedAttack.class);
		when(bartizan.isRanged()).thenReturn(true);

		assertEquals(10.0, NpcFireRate.scale(0.1, bartizan), 1e-9);
		assertEquals(20.0, NpcFireRate.scale(0.05, bartizan), 1e-9);
	}

	@Test
	@DisplayName("a vanilla bow/crossbow gets half: Keystone's 30-tick base x 5 = 150 ticks, 0.11's 15 AI ticks x 10")
	void vanillaBow_halfScale() {
		assertEquals(5.0, NpcFireRate.scale(0.1, NpcRangedAttack.NONE), 1e-9);
		assertEquals(10.0, NpcFireRate.scale(0.05, NpcRangedAttack.NONE), 1e-9);
	}
}
