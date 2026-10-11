package org.luckyraven.gangland.civilians.npc.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CivilianNpc.applyRole - placement, leader priority and the clamped firing band, once at spawn")
class CivilianNpcRoleTest {

	private static final NpcRole MARKSMAN = new NpcRole("Marksman", "Marksman", NpcFanPlacement.ANY, 12.0, 24.0, 1.0,
	                                                    3, null, 1.0, 0, null, 0, 0, false, false);

	@Test
	@DisplayName("a shooter takes the role's placement, priority and band")
	void shooter_takesRole() {
		CivilianNpc npc = mock(CivilianNpc.class);
		doCallRealMethod().when(npc).applyRole(MARKSMAN);
		when(npc.isRangedAttacker()).thenReturn(true);

		npc.applyRole(MARKSMAN);

		verify(npc).setFanPlacement(NpcFanPlacement.ANY);
		verify(npc).setLeaderPriority(3);
		verify(npc).setRangedBand(12.0, 24.0);
	}

	@Test
	@DisplayName("an NPC with no gun keeps the default band: a Marksman must not stand 12-24 blocks off unarmed")
	void unarmed_keepsDefaultBand() {
		CivilianNpc npc = mock(CivilianNpc.class);
		doCallRealMethod().when(npc).applyRole(MARKSMAN);
		when(npc.isRangedAttacker()).thenReturn(false);

		npc.applyRole(MARKSMAN);

		verify(npc, never()).setRangedBand(anyDouble(), anyDouble());
		verify(npc).setLeaderPriority(3);
	}
}
