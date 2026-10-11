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
		listener = new FightGangDeleteListener(rig.manager, rig.settlement);
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
	@DisplayName("in the countdown the survivor is paid the escrow and the fight ends")
	void disband_inCountdown_paysSurvivor() {
		Fight fight = rig.countdown(50);

		listener.onGangDelete(deleted(FightRig.RATS));

		assertEquals(FightState.RESULT, fight.state());
		verify(rig.settlement).settle(fight, Outcome.WIN_A);
		verify(rig.settlement, never()).refund(any());
	}

	@Test
	@DisplayName("in the lobby the survivor is paid the escrow before the roster collapses")
	void disband_inLobby_paysSurvivor() {
		rig.manager.propose(rig.a.getUniqueId(), "Rats", 1, 50);
		rig.manager.accept(rig.b.getUniqueId(), "Vipers");
		Fight fight = rig.manager.active().iterator().next();
		rig.manager.enlist(rig.a.getUniqueId());
		assertEquals(FightState.LOBBY, fight.state());

		listener.onGangDelete(deleted(FightRig.RATS));

		verify(rig.settlement).settle(fight, Outcome.WIN_A);
	}
}
