package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): a {@link CopGroup} owns one Keystone squad. A cop joins it when it is assigned to the group and
 * leaves it when it is released (despawned, killed, invalid); {@link CopNpc#squadFor} shares the squad only for the
 * group's own wanted player, so a retargeted cop or an entity target never feeds the group's last-known position.
 */
@DisplayName("CopGroup - squad membership")
class CopGroupSquadTest {

	@Test
	@DisplayName("an assigned cop joins the squad; a released cop is destroyed and leaves it")
	void addAndRelease_trackSquadMembership() {
		CopGroup group = new CopGroup(UUID.randomUUID());
		CopNpc   cop   = mock(CopNpc.class);

		group.add(cop);

		assertEquals(List.of(cop), group.getSquad().members());
		verify(cop).setGroup(group);

		group.release(cop, mock(NpcMarkManager.class));

		verify(cop).destroy(any());
		assertTrue(group.getSquad().isEmpty());
	}

	@Test
	@DisplayName("squadFor shares the group squad only for the group's wanted player; leaveSquad drops the cop")
	void squadFor_sharesOnlyForTheGroupTarget_andLeaveSquadDropsTheCop() {
		UUID     wantedId = UUID.randomUUID();
		CopGroup group    = new CopGroup(wantedId);
		CopNpc   cop      = mock(CopNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC behind it
		cop.setGroup(group);
		group.getSquad().add(cop);

		LivingEntity wanted = mock(LivingEntity.class);
		when(wanted.getUniqueId()).thenReturn(wantedId);
		LivingEntity other = mock(LivingEntity.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());

		assertSame(group.getSquad(), cop.squadFor(wanted));
		assertNull(cop.squadFor(other));

		cop.leaveSquad();

		assertTrue(group.getSquad().isEmpty());
	}
}
