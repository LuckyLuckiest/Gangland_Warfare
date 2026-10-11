package org.luckyraven.gangland.fights.listener;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.fights.fight.Fight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("FightWantedGuard - a fighter never gains a wanted star in a fight (GP-41)")
class FightWantedGuardTest {

	private FightRig         rig;
	private FightWantedGuard guard;

	@BeforeEach
	void setUp() {
		rig   = new FightRig();
		guard = new FightWantedGuard(rig.manager);
	}

	private WantedLevelChangeEvent change(Player player, int from, int to) {
		WantedLevelChangeEvent event = new WantedLevelChangeEvent(player, mock(Wanted.class), from, to);
		guard.onWantedChange(event);
		return event;
	}

	@Test
	@DisplayName("a raise for a fighter in the countdown is cancelled")
	void participantWantedChange_cancelled() {
		rig.countdown(0);

		assertTrue(change(rig.a, 0, 1).isCancelled());
	}

	@Test
	@DisplayName("a raise during RUNNING is cancelled, a drop is let through, and an outsider is untouched")
	void running_dropAndOutsider() {
		rig.running(0);

		assertTrue(change(rig.a, 0, 2).isCancelled());
		assertFalse(change(rig.a, 3, 1).isCancelled());
		assertFalse(change(rig.player(), 0, 1).isCancelled());
	}

	@Test
	@DisplayName("an eliminated fighter is a normal player again: no crime immunity while the fight runs on")
	void eliminated_notCovered() {
		Fight fight = rig.running(0);
		fight.eliminate(rig.a.getUniqueId());

		assertFalse(change(rig.a, 0, 1).isCancelled());
	}

	@Test
	@DisplayName("a player still in the lobby is a normal player")
	void lobby_allowed() {
		rig.manager.propose(rig.a.getUniqueId(), "Rats", 1, 0);
		rig.manager.accept(rig.b.getUniqueId(), "Vipers");
		rig.manager.enlist(rig.a.getUniqueId());

		assertFalse(change(rig.a, 0, 1).isCancelled());
	}
}
