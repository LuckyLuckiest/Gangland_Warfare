package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcMeleeProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopNpcFactory.meleeFor - a tier's approach is clamped below its own Cuff_Radius")
class CopNpcFactoryMeleeTest {

	@Test
	@DisplayName("approach clamps to Cuff_Radius - 0.5 when the melee profile's approach would reach past it")
	void meleeFor_clampsApproachBelowCuffRadius() {
		NpcMeleeProfile profile = new NpcMeleeProfile(3.0, 2.0, 12, 0.15, 0.7);
		CopTierConfig   tier    = tierWithCuffRadius(1.2); // approach 2.0 must clamp to 1.2 - 0.5 = 0.7

		NpcMeleeProfile result = CopNpcFactory.meleeFor(profile, tier);

		assertEquals(0.7, result.approach());
		assertEquals(profile.reach(), result.reach());
		assertEquals(profile.cooldownTicks(), result.cooldownTicks());
		assertEquals(profile.damageSpread(), result.damageSpread());
		assertEquals(profile.edgeDamage(), result.edgeDamage());
	}

	@Test
	@DisplayName("a spacious Cuff_Radius leaves the profile's own approach untouched")
	void meleeFor_leavesApproachWhenCuffRadiusIsSpacious() {
		NpcMeleeProfile profile = new NpcMeleeProfile(3.0, 2.0, 12, 0.15, 0.7);
		CopTierConfig   tier    = tierWithCuffRadius(4.0);

		NpcMeleeProfile result = CopNpcFactory.meleeFor(profile, tier);

		assertEquals(2.0, result.approach());
	}

	@Test
	@DisplayName("applyTuning wires the tier's engagement, the clamped melee profile and 1 / Fire_Rate_Multiplier")
	void applyTuning_setsEngagementMeleeAndFireRateScale() {
		CopTierConfig     tier     = tierWithCuffRadius(4.0);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getMeleeProfile()).thenReturn(NpcMeleeProfile.DEFAULT);
		CopNpc copNpc = mock(CopNpc.class);

		CopNpcFactory.applyTuning(copNpc, tier, provider);

		verify(copNpc).setEngagement(tier.tactics().engagement());
		verify(copNpc).setMeleeProfile(CopNpcFactory.meleeFor(NpcMeleeProfile.DEFAULT, tier));
		verify(copNpc).setFireRateScale(4.0); // Fire_Rate_Multiplier 0.25 = a quarter of the weapon's own rate
	}

	private static CopTierConfig tierWithCuffRadius(double cuffRadius) {
		return new CopTierConfig(1, "&9Officer", 20.0, 2.0, 1.0, cuffRadius, false, false, List.of(), List.of(),
		                         null, null, null, null, NpcDifficulty.EASY, TacticsConfig.DEFAULT, 0.25);
	}
}
