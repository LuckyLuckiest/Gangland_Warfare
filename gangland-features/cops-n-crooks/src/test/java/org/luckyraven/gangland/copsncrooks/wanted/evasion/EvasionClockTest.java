package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvasionClock}: the SEEN / SEARCHING / EVADED / OFF machine driven by hand-fed AI ticks and a fake clock, the
 * drop through {@code WantedStars.drop(.., EVASION)}, the zone speed, and the decay-policy answer.
 */
@DisplayName("EvasionClock")
class EvasionClockTest {

	private final long[]      now    = {1_000_000L};
	private final List<Event> events = new ArrayList<>();
	private final World       world  = mock(World.class);
	private final UUID        id     = UUID.randomUUID();
	private final Location    centre = new Location(world, 0, 64, 0);

	private EvasionSettings   settings;
	private ChaseConfigLoader config;
	private CopManager        copManager;
	private DetainmentService detainment;
	private WantedStars       stars;
	private Player            player;
	private Wanted            wanted;
	private CopGroup          group;
	private NpcSquad          squad;
	private long              unseenMs;
	private Location          playerAt;
	private EvasionClock      clock;

	@BeforeEach
	void setUp() {
		settings = EvasionSettings.DEFAULT;
		config   = mock(ChaseConfigLoader.class);
		when(config.get()).thenAnswer(inv -> new ChaseConfig(null, settings, null, null));
		copManager = mock(CopManager.class);
		detainment = mock(DetainmentService.class);
		stars      = mock(WantedStars.class);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		playerAt = new Location(world, 5, 64, 5);
		when(player.getLocation()).thenAnswer(inv -> playerAt.clone());

		wanted = new Wanted(mock(JavaPlugin.class), 1, 5);
		wanted.setLevel(2);
		@SuppressWarnings("unchecked") User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		@SuppressWarnings("unchecked") UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		squad    = mock(NpcSquad.class);
		unseenMs = Long.MAX_VALUE;
		when(squad.millisSinceSighting()).thenAnswer(inv -> unseenMs);
		when(squad.lastKnownLocation()).thenAnswer(inv -> centre.clone());
		group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		List<CopNpc> hunters = List.of(cop(CopState.PURSUING));
		when(group.getCops()).thenReturn(hunters);
		when(group.tippedOffWithin(anyLong(), anyLong())).thenReturn(false);
		when(copManager.groupOf(id)).thenReturn(group);

		// the real drop lowers the level; the mock does the same so the clock can read the result
		when(stars.drop(any(), anyInt(), any())).thenAnswer(inv -> {
			int n = inv.getArgument(1);
			wanted.setLevel(wanted.getLevel() - n);
			return n;
		});

		clock = new EvasionClock(config, copManager, detainment, stars, users, () -> now[0], events::add);
	}

	private static CopNpc cop(CopState state) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenReturn(state);
		return cop;
	}

	/** Advances the fake clock by {@code seconds} one-second ticks, running the hook each second. */
	private void tickSeconds(int seconds) {
		for (int i = 0; i < seconds; i++) {
			now[0] += 1000;
			clock.tick(player, group);
		}
	}

	private List<WantedEvasionStateEvent> states() {
		return events.stream().filter(WantedEvasionStateEvent.class::isInstance)
		             .map(WantedEvasionStateEvent.class::cast).toList();
	}

	private List<EvasionState> stateNames() {
		return states().stream().map(WantedEvasionStateEvent::getState).toList();
	}

	@Test
	@DisplayName("a cop that has just seen the player holds the clock and fires SEEN exactly once")
	void seen_holds_andFiresSeenOnce() {
		unseenMs = 500;

		tickSeconds(5);

		assertEquals(List.of(EvasionState.SEEN), stateNames());
		assertEquals(EvasionState.SEEN, clock.snapshot(id).state());
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("unseen past Lost_Sight_Seconds starts the search with the level countdown and radius, centred on the last sighting")
	void unseenPastLostSight_searches_withTheLevelsCountdownAndRadius() {
		unseenMs = 3_000;

		tickSeconds(1);

		WantedEvasionStateEvent e = states().get(0);
		assertEquals(EvasionState.SEARCHING, e.getState());
		assertEquals(2, e.getLevel());
		assertEquals(20, e.getSecondsLeft());
		assertEquals(60.0, e.getZoneRadius());
		assertEquals(centre, e.getZoneCentre());
	}

	@Test
	@DisplayName("twenty seconds unseen inside the zone drops one star with the EVASION cause and fires EVADED")
	void twentySecondsUnseenInsideTheZone_dropsOneStarWithEvasionCause_andFiresEvaded() {
		unseenMs = 10_000;

		tickSeconds(21);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
		WantedEvasionStateEvent last = states().get(states().size() - 1);
		assertEquals(EvasionState.EVADED, last.getState());
		assertEquals(1, last.getLevel());
		assertEquals(40.0, last.getZoneRadius());
		assertEquals(centre, last.getZoneCentre());
	}

	@Test
	@DisplayName("outside the zone the countdown runs twice as fast: a two-star drop takes 10 s")
	void outsideTheZone_countsTwiceAsFast() {
		unseenMs = 10_000;
		playerAt = new Location(world, 500, 64, 0);

		tickSeconds(11);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("being spotted again resets the countdown and turns SEEN")
	void spottedAgain_resetsTheCountdown_andTurnsSeen() {
		unseenMs = 10_000;
		tickSeconds(10);
		unseenMs = 100;
		tickSeconds(1);
		assertEquals(EvasionState.SEEN, clock.snapshot(id).state());

		unseenMs = 10_000;
		tickSeconds(15);

		verify(stars, never()).drop(any(), anyInt(), any());
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(6, clock.snapshot(id).secondsLeft());
	}

	@Test
	@DisplayName("Drop_Mode ALL_STARS drops every star at once")
	void allStarsMode_dropsEveryStar() {
		settings = new EvasionSettings(true, 3, DropMode.ALL_STARS, settings.searchRadius(), settings.secondsToDrop(),
		                               2.0);
		wanted.setLevel(3);
		unseenMs = 10_000;

		tickSeconds(31);

		verify(stars).drop(any(), eq(3), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("a restrained player holds the clock")
	void restrained_holds() {
		when(detainment.isRestrained(player)).thenReturn(true);
		unseenMs = 10_000;

		tickSeconds(30);

		assertTrue(events.isEmpty());
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("a recent stuck-recycle tip-off holds the clock")
	void tipOff_holds() {
		when(group.tippedOffWithin(anyLong(), eq(3000L))).thenReturn(true);
		unseenMs = 10_000;

		tickSeconds(30);

		assertTrue(events.isEmpty());
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("only returning cops: the clock does not handle decay and a tracked player turns OFF")
	void onlyReturningCops_doesNotHandleDecay_andTurnsOff() {
		unseenMs = 10_000;
		tickSeconds(1);
		assertTrue(clock.handlesDecay(player, wanted));

		List<CopNpc> walkingHome = List.of(cop(CopState.RETURNING));
		when(group.getCops()).thenReturn(walkingHome);
		assertFalse(clock.handlesDecay(player, wanted));
		tickSeconds(1);
		assertEquals(EvasionState.OFF, states().get(states().size() - 1).getState());
		assertNull(clock.snapshot(id));
	}

	@Test
	@DisplayName("evasion disabled: the clock does not handle decay, a tracked player turns OFF once and no star drops")
	void disabled_doesNotHandleDecay_turnsOffOnce_andNeverDrops() {
		unseenMs = 10_000;
		tickSeconds(1);
		assertTrue(clock.handlesDecay(player, wanted));

		settings = new EvasionSettings(false, 3, DropMode.ONE_STAR, settings.searchRadius(), settings.secondsToDrop(),
		                               2.0);
		assertFalse(clock.handlesDecay(player, wanted));
		tickSeconds(40);

		assertEquals(1, stateNames().stream().filter(EvasionState.OFF::equals).count());
		assertNull(clock.snapshot(id));
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("a SEARCHING player whose cops are all gone turns OFF once and decay is no longer handled")
	void emptyGroup_whileSearching_turnsOff() {
		unseenMs = 10_000;
		tickSeconds(2);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());

		when(group.getCops()).thenReturn(List.of());
		tickSeconds(3);

		assertEquals(1, stateNames().stream().filter(EvasionState.OFF::equals).count());
		assertNull(clock.snapshot(id));
		assertFalse(clock.handlesDecay(player, wanted));
	}

	@Test
	@DisplayName("a SEARCHING player whose group is gone (null) turns OFF once")
	void nullGroup_whileSearching_turnsOff() {
		unseenMs = 10_000;
		tickSeconds(2);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());

		for (int i = 0; i < 3; i++) {
			now[0] += 1000;
			clock.tick(player, null);
		}

		assertEquals(1, stateNames().stream().filter(EvasionState.OFF::equals).count());
		assertNull(clock.snapshot(id));
		when(copManager.groupOf(id)).thenReturn(null);
		assertFalse(clock.handlesDecay(player, wanted));
	}

	@Test
	@DisplayName("dropping the last star ends the chase: after the end-of-wanted clear nothing more fires")
	void lastStarDrop_firesNoEvadedAfterTheChaseEnds() {
		wanted.setLevel(1);
		unseenMs = 10_000;
		tickSeconds(11);
		clock.clear(player); // what EvasionListener does on the WantedEndEvent the drop raised
		int fired = events.size();

		tickSeconds(3);

		assertEquals(EvasionState.OFF, states().get(states().size() - 1).getState());
		assertFalse(stateNames().contains(EvasionState.EVADED));
		assertEquals(fired, events.size());
	}

	@Test
	@DisplayName("after a drop the search continues at the new level with the same centre")
	void afterADrop_theSearchContinuesAtTheNewLevel() {
		unseenMs = 10_000;
		tickSeconds(21);

		tickSeconds(1);

		WantedEvasionStateEvent e = states().get(states().size() - 1);
		assertEquals(EvasionState.SEARCHING, e.getState());
		assertEquals(1, e.getLevel());
		assertEquals(10, e.getSecondsLeft());
		assertEquals(40.0, e.getZoneRadius());
		assertEquals(centre, e.getZoneCentre());
	}

	@Test
	@DisplayName("the countdown event fires only when the second changes")
	void countdownEvent_onlyWhenTheSecondChanges() {
		unseenMs = 10_000;
		tickSeconds(1);
		int afterEntry = events.size();

		for (int i = 0; i < 4; i++) {
			now[0] += 250;
			clock.tick(player, group);
		}

		assertEquals(afterEntry + 1, events.size());
		assertSame(EvasionState.SEARCHING, states().get(states().size() - 1).getState());
	}
}
