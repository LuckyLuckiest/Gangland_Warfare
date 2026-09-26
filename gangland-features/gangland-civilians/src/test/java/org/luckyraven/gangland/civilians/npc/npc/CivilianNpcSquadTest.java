package org.luckyraven.gangland.civilians.npc.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Phase H11 (spec 4.10): a civilian is in at most one squad - the one hunting its current target. Joining another
 * leaves the first; leaving frees its slot and clears the target id.
 */
@DisplayName("CivilianNpc - squad membership")
class CivilianNpcSquadTest {

	@Test
	@DisplayName("joining a second squad leaves the first; leaving clears squad and target")
	void joinSwitchLeave() {
		CivilianNpc npc     = mock(CivilianNpc.class, CALLS_REAL_METHODS); // real squad methods, no Citizens NPC
		NpcSquad    first   = new NpcSquad();
		NpcSquad    second  = new NpcSquad();
		UUID        targetA = UUID.randomUUID();
		UUID        targetB = UUID.randomUUID();

		npc.joinSquad(first, targetA);

		assertEquals(List.of(npc), first.members());
		assertSame(first, npc.getSquad());
		assertEquals(targetA, npc.getSquadTargetId());

		npc.joinSquad(second, targetB);

		assertTrue(first.isEmpty());
		assertEquals(List.of(npc), second.members());
		assertEquals(targetB, npc.getSquadTargetId());

		npc.leaveSquad();

		assertTrue(second.isEmpty());
		assertNull(npc.getSquad());
		assertNull(npc.getSquadTargetId());
	}
}
