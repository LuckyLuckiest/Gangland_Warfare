package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings.Result;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * P-PAT-2: the "put that away" warning clock. 5 s to comply, then a seen {@code Brandish_Near_Cop}; one warning per
 * player per 30 s counted from when it was given.
 */
@DisplayName("BrandishWarnings - warning clock and repeat cooldown")
class BrandishWarningsTest {

	private PatrolFixture    fx;
	private CrimeService     crimes;
	private WantedMessages   messages;
	private WarningSettings  settings;
	private BrandishWarnings warnings;
	private Player           player;

	@BeforeEach
	void setUp() {
		fx       = new PatrolFixture();
		crimes   = mock(CrimeService.class);
		messages = PatrolFixture.messages();
		settings = WarningSettings.DEFAULT;
		warnings = new BrandishWarnings(() -> settings, () -> fx.clock[0], crimes,
		                                p -> fx.armed.contains(p.getUniqueId()), messages);
		player   = fx.player(3, 3);
		fx.armed.add(player.getUniqueId());
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("begin sends Patrol.Put_Away with the seconds and opens the warning")
	void begin_sendsPutAway() {
		assertTrue(warnings.canWarn(player));

		assertTrue(warnings.begin(player));

		verify(messages).format(WantedMessages.Key.PATROL_PUT_AWAY, Map.of("seconds", "5"));
		verify(player).sendMessage("PATROL_PUT_AWAY");
		assertFalse(warnings.canWarn(player));
	}

	@Test
	@DisplayName("holding is the injected predicate")
	void holding_isThePredicate() {
		assertTrue(warnings.holding(player));
		fx.armed.clear();
		assertFalse(warnings.holding(player));
	}

	@Test
	@DisplayName("weapon held 5 s in view commits Brandish_Near_Cop as seen by a cop, exactly once")
	void ignoredWarningCommitsSeenBrandish() {
		warnings.begin(player);

		fx.clock[0] += 4_999;
		assertEquals(Result.PENDING, warnings.update(player, true));
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());

		fx.clock[0] += 1;
		assertEquals(Result.IGNORED, warnings.update(player, true));
		verify(crimes, times(1)).commit(eq(player), eq(Crimes.BRANDISH_NEAR_COP), any(Location.class), eq(true), eq(0));

		warnings.update(player, true);
		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("ignored: the player is told the cause (Patrol.Ignored) before the crime is committed")
	void ignoredWarning_namesTheCause_beforeTheStar() {
		warnings.begin(player);
		fx.clock[0] += 5_000;

		warnings.update(player, true);

		org.mockito.InOrder order = org.mockito.Mockito.inOrder(player, crimes);
		order.verify(player).sendMessage("PATROL_IGNORED");
		order.verify(crimes).commit(eq(player), eq(Crimes.BRANDISH_NEAR_COP), any(Location.class), eq(true), eq(0));
		verify(messages).format(WantedMessages.Key.PATROL_IGNORED, Map.of("cause", Crimes.BRANDISH_NEAR_COP));
	}

	@Test
	@DisplayName("the crime is committed at the player's location")
	void crimeLocationIsThePlayers() {
		warnings.begin(player);
		fx.clock[0] += 5_000;

		warnings.update(player, true);

		verify(crimes).commit(player, Crimes.BRANDISH_NEAR_COP, fx.at(3, 3), true, 0);
	}

	@Test
	@DisplayName("weapon gone at 3 s: no crime, Patrol.Complied")
	void putAwayNoCrime() {
		warnings.begin(player);
		fx.clock[0] += 3_000;
		fx.armed.clear();

		assertEquals(Result.COMPLIED, warnings.update(player, true));

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(messages).format(WantedMessages.Key.PATROL_COMPLIED, Map.of());
		verify(player).sendMessage("PATROL_COMPLIED");
	}

	@Test
	@DisplayName("still holding at 5 s but out of the cop's view: complied, no crime")
	void outOfViewAtDeadline_noCrime() {
		warnings.begin(player);
		fx.clock[0] += 5_000;

		assertEquals(Result.COMPLIED, warnings.update(player, false));

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(player).sendMessage("PATROL_LOST_SIGHT");
	}

	@Test
	@DisplayName("Seconds 0: an armed player in view is ignored at once")
	void zeroSeconds_ignoredAtOnce() {
		settings = new WarningSettings(true, 12.0, 0, 30);
		warnings.begin(player);

		assertEquals(Result.IGNORED, warnings.update(player, true));
	}

	@Test
	@DisplayName("update without an open warning does nothing and is COMPLIED")
	void updateWithoutWarning_isNoop() {
		assertEquals(Result.COMPLIED, warnings.update(player, true));

		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a second begin while a warning is open is refused and says nothing")
	void secondBeginRefused() {
		warnings.begin(player);

		assertFalse(warnings.begin(player));

		verify(player, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("the cooldown counts from the verdict: 29.999 s after it no warning, 30 s after it a warning")
	void repeatCooldown() {
		warnings.begin(player);
		fx.clock[0] += 3_000;
		fx.armed.clear();
		warnings.update(player, true);
		fx.armed.add(player.getUniqueId());

		fx.clock[0] += 29_999;
		assertFalse(warnings.canWarn(player));
		assertFalse(warnings.begin(player));

		fx.clock[0] += 1;
		assertTrue(warnings.canWarn(player));
		assertTrue(warnings.begin(player));
	}

	@Test
	@DisplayName("an ignored warning also starts the cooldown at its verdict, not at its start")
	void repeatCooldown_ignoredVerdict() {
		warnings.begin(player);
		fx.clock[0] += 5_000;
		warnings.update(player, true);

		fx.clock[0] += 29_999;
		assertFalse(warnings.canWarn(player));

		fx.clock[0] += 1;
		assertTrue(warnings.canWarn(player));
	}

	@Test
	@DisplayName("the cooldown is per player")
	void cooldownIsPerPlayer() {
		Player other = fx.player(4, 4);
		warnings.begin(player);

		assertTrue(warnings.canWarn(other));
	}

	@Test
	@DisplayName("Cops.Warning.Enable false: nobody can be warned")
	void disabled_noWarnings() {
		settings = new WarningSettings(false, 12.0, 5, 30);

		assertFalse(warnings.canWarn(player));
		assertFalse(warnings.begin(player));
	}

	@Test
	@DisplayName("abandon drops the open warning with no verdict; the cooldown stays")
	void abandon_dropsWarningKeepsCooldown() {
		warnings.begin(player);

		warnings.abandon(player.getUniqueId());
		fx.clock[0] += 6_000;

		assertEquals(Result.COMPLIED, warnings.update(player, true));
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertFalse(warnings.canWarn(player));
	}
}
