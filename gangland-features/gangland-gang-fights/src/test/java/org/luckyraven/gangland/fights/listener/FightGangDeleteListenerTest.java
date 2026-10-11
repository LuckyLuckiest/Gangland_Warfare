package org.luckyraven.gangland.fights.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightState;
import org.luckyraven.gangland.fights.fight.Outcome;
import org.luckyraven.gangland.gang.Gang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FightGangDeleteListener - disbanding a gang mid-fight forfeits it (GP-31)")
class FightGangDeleteListenerTest {

	private FightRig                rig;
	private FightGangDeleteListener listener;

	@BeforeEach
	void setUp() {
		rig      = new FightRig();
		listener = new FightGangDeleteListener(rig.manager);
	}

	private GangDeleteEvent deleted(int gangId) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(gangId);
		return new GangDeleteEvent(gang);
	}

	@Test
	@DisplayName("the disbanded gang forfeits, the survivor wins and settlement pays it the escrow")
	void disband_forfeitPaysSurvivor() {
		Fight fight = rig.running(50);

		listener.onGangDelete(deleted(FightRig.RATS));

		assertEquals(FightState.RESULT, fight.state());
		verify(rig.settlement).settle(fight, Outcome.WIN_A);
	}

	@Test
	@DisplayName("it works for the challenger side too")
	void disband_challengerSide() {
		Fight fight = rig.running(0);

		listener.onGangDelete(deleted(FightRig.VIPERS));

		verify(rig.settlement).settle(fight, Outcome.WIN_B);
	}

	@Test
	@DisplayName("a gang that is not in a fight changes nothing")
	void unrelatedGang_ignored() {
		Fight fight = rig.running(0);

		listener.onGangDelete(deleted(99));

		assertEquals(FightState.RUNNING, fight.state());
		verify(rig.settlement, never()).settle(any(), any());
	}

	@Test
	@DisplayName("in the countdown the fight is cancelled and the stakes refunded")
	void disband_inCountdown_refunds() {
		Fight fight = rig.countdown(50);

		listener.onGangDelete(deleted(FightRig.RATS));

		assertEquals(FightState.CANCELLED, fight.state());
		verify(rig.settlement).refund(fight);
		assertTrue(rig.manager.active().isEmpty());
	}
}
