package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CopRetreat - a role's own retreat threshold, and the squad falling back after its Commander went down")
class CopRetreatTest {

	private final long[]       clock   = {10_000L};
	private final CopRetreat   retreat = new CopRetreat(new RetreatSettings(true, 0.3, 12.0), () -> clock[0]);
	private final LivingEntity target  = mock(LivingEntity.class);

	@Test
	@DisplayName("a role Retreat.Health_Fraction 0.15 fights on at 25% health, where the global 0.3 retreats")
	void roleThreshold_beatsGlobal() {
		CopNpc plain    = cop(5.0, null);
		CopNpc defender = cop(5.0, role(new RetreatSettings(true, 0.15, 12.0)));

		assertTrue(retreat.takeCover(plain, target));
		assertFalse(retreat.takeCover(defender, target));
	}

	@Test
	@DisplayName("while its group falls back, a healthy cop takes cover; after the deadline it fights on")
	void groupFallBack_healthyCopTakesCover_untilDeadline() {
		CopGroup group = new CopGroup(UUID.randomUUID());
		CopNpc   cop   = cop(20.0, null);
		when(cop.getGroup()).thenReturn(group);
		group.setFallBackUntil(clock[0] + 5_000);

		assertTrue(retreat.takeCover(cop, target));

		clock[0] += 5_000;
		assertFalse(retreat.takeCover(cop, target));
	}

	private static CopNpc cop(double health, CopRole role) {
		CopNpc       cop  = mock(CopNpc.class);
		LivingEntity self = mock(LivingEntity.class);
		when(self.getHealth()).thenReturn(health);
		when(self.getMaxHealth()).thenReturn(20.0);
		when(cop.getEntity()).thenReturn(self);
		when(cop.getRole()).thenReturn(role);
		when(cop.takeCover(any(), anyDouble(), any())).thenReturn(NpcCoverStatus.MOVING);
		return cop;
	}

	private static CopRole role(RetreatSettings retreat) {
		return new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, null, null, 1.0, null, 0, null, 1.0, 0,
		                   retreat, 0.5, 60, false, false);
	}
}
