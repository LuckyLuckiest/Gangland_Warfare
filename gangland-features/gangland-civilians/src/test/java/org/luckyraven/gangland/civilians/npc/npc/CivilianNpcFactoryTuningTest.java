package org.luckyraven.gangland.civilians.npc.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcMeleeProfile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CivilianNpcFactory.applyTuning - engagement, melee profile and fire-rate scale from the type's AI.Combat")
class CivilianNpcFactoryTuningTest {

	@Test
	@DisplayName("applyTuning wires the type's engagement, melee profile and 1 / Fire_Rate_Multiplier")
	void applyTuning_setsEngagementMeleeAndFireRateScale() {
		NpcMeleeProfile melee = new NpcMeleeProfile(3.0, 2.0, 20, 0.2, 0.7);
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, 16.0, 20,
		                                                           TacticsConfig.DEFAULT, melee, 0.05,
		                                                           RetreatSettings.DEFAULT);
		CivilianNpc civilian = mock(CivilianNpc.class);

		CivilianNpcFactory.applyTuning(civilian, ai);

		verify(civilian).setEngagement(TacticsConfig.DEFAULT.engagement());
		verify(civilian).setMeleeProfile(melee);
		verify(civilian).setFireRateScale(20.0); // 0.05 = a twentieth of the weapon's own rate
	}
}
