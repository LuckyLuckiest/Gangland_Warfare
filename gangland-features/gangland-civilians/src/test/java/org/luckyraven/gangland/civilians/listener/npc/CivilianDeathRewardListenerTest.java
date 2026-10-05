package org.luckyraven.gangland.civilians.listener.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins that a civilian kill is reported as a Kill_Civilian crime (once) instead of bumping wanted directly.
 */
@DisplayName("CivilianDeathRewardListener")
class CivilianDeathRewardListenerTest {

	private Player                      killer;
	private Location                    location;
	private Wanted                      wanted;
	private Level                       level;
	private CrimeService                crimes;
	private CivilianNpc                 npc;
	private CivilianDeathRewardListener listener;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		killer   = mock(Player.class);
		location = mock(Location.class);
		when(killer.getLocation()).thenReturn(location);

		wanted = new Wanted(null, 1, 5);
		level  = mock(Level.class);
		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		when(user.getLevel()).thenReturn(level);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(killer)).thenReturn(user);

		crimes   = mock(CrimeService.class);
		npc      = mock(CivilianNpc.class);
		listener = new CivilianDeathRewardListener(users, crimes);
	}

	@Test
	@DisplayName("a civilian kill commits Kill_Civilian and does not increment wanted")
	void civilianKill_commitsKillCivilian_andDoesNotIncrementWanted() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killer, 0));

		verify(crimes).commit(killer, Crimes.KILL_CIVILIAN, location);
		assertEquals(0, wanted.getLevel());
	}

	@Test
	@DisplayName("a hostile civilian killed in combat commits nothing")
	void hostileCivilianInCombat_commitsNothing() {
		when(npc.isHostile()).thenReturn(true);
		when(npc.getCurrentState()).thenReturn(CivilianState.COMBAT);

		listener.onCivilianDeath(new CivilianDeathEvent(npc, killer, 0));

		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("the XP is still awarded")
	void xpIsStillAwarded() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killer, 12.5));

		verify(level).addExperience(anyDouble(), any());
	}
}
