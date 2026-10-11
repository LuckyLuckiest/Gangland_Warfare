package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("fight seams - data holders and inert stubs")
class FightSeamsTest {

	@Test
	@DisplayName("a new fight is PROPOSED and finds its participants")
	void fight_holdsState() {
		Fight fight  = new Fight(7L, 1, 2, 3, 500D, true, 1000L);
		UUID  player = UUID.randomUUID();

		assertEquals(FightState.PROPOSED, fight.state());
		assertNull(fight.arenaName());
		fight.addParticipant(new Participant(player, 1, FightSide.A));
		fight.state(FightState.LOBBY);

		assertEquals(FightSide.A, fight.participant(player).orElseThrow().side());
		assertTrue(fight.participant(UUID.randomUUID()).isEmpty());
		assertEquals(FightState.LOBBY, fight.state());
	}

	@Test
	@DisplayName("the stubs run nothing, find nothing, and escrow fails closed")
	void stubs_areInert() {
		FightManager manager = new FightManager();
		UUID         player  = UUID.randomUUID();

		manager.abortAll("test");
		assertFalse(manager.isParticipant(player));
		assertTrue(manager.fightOf(player).isEmpty());
		assertTrue(manager.active().isEmpty());
		assertTrue(new ArenaRegistry().enabled().isEmpty());
		assertFalse(new FightSettlement().escrow(new Fight(1L, 1, 2, 1, 10D, false, 0L)).ok());
		assertTrue(EscrowResult.success().ok());
		assertEquals("k", EscrowResult.fail("k").failureKey());
	}
}
