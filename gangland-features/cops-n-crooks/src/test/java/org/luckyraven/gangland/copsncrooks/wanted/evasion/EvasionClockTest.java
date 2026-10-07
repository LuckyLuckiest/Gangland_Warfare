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
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLearner;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
	private HeatLedger        ledger;
	private List<CrimeRecord> crimes;
	private ChaseArcs         arcs;
	private ChaseLearner      learner;
	private List<DropPlan>    plansAtDrop;
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

		crimes = new ArrayList<>();
		ledger = mock(HeatLedger.class);
		when(ledger.chaseCrimes(id)).thenAnswer(inv -> List.copyOf(crimes));
		arcs    = spy(new ChaseArcs(() -> now[0]));
		learner = mock(ChaseLearner.class);
		when(learner.typicalSeconds(anyInt(), any())).thenAnswer(
				inv -> (double) inv.<AutoSettings>getArgument(1).typicalFor(inv.getArgument(0)));

		// the real drop lowers the level; the mock does the same so the clock can read the result. Like the HUD, it
		// takes the plan the clock stashed, inside the drop
		plansAtDrop = new ArrayList<>();
		when(stars.drop(any(), anyInt(), any())).thenAnswer(inv -> {
			int n = inv.getArgument(1);
			plansAtDrop.add(arcs.takePending(id));
			wanted.setLevel(wanted.getLevel() - n);
			return n;
		});

		clock = new EvasionClock(config, copManager, detainment, stars, users, ledger, arcs, learner, () -> now[0],
		                         events::add);
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

	// ---- Drop_Mode AUTO (0.15.2) ----

	private void auto() {
		auto(settings.secondsToDrop());
	}

	private void auto(List<Integer> secondsToDrop) {
		settings = new EvasionSettings(true, 3, DropMode.AUTO, settings.searchRadius(), secondsToDrop, 2.0,
		                               AutoSettings.DEFAULT);
	}

	private void crimeAt(long at) {
		crimes.add(new CrimeRecord("Kill_Civilian", 100, at, centre));
	}

	/** The countdown each search spell opened with: the first SEARCHING after anything else. */
	private List<Integer> spellCountdowns() {
		List<Integer> out  = new ArrayList<>();
		EvasionState  prev = null;
		for (WantedEvasionStateEvent e : states()) {
			if (e.getState() == EvasionState.SEARCHING && prev != EvasionState.SEARCHING) out.add(e.getSecondsLeft());
			prev = e.getState();
		}
		return out;
	}

	private List<Ending> endings() {
		return plansAtDrop.stream().map(plan -> plan == null ? null : plan.ending()).toList();
	}

	@Test
	@DisplayName("AUTO E1: a small chase drops both stars at once, the PETTY plan stashed before stars.drop")
	void auto_smallChase_dropsBothStarsAtOnce_withThePlanStashedBeforeTheDrop() {
		auto();
		arcs.start(id, WantedCause.CRIME, 2);
		crimeAt(now[0] - 25_000);
		crimeAt(now[0] - 15_000);
		unseenMs = 10_000;

		tickSeconds(21);

		verify(stars).drop(any(), eq(2), eq(WantedCause.EVASION));
		assertEquals(List.of(new DropPlan(2, Ending.PETTY, AutoDropPlanner.REASON_NONE)), plansAtDrop);
	}

	@Test
	@DisplayName("AUTO E2: hunkering inside the zone needs 30 s, then 15 s, then 6 s")
	void auto_hunkerCascade_needs30Then15Then6Seconds() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		crimeAt(now[0] - 60_000);
		crimeAt(now[0] - 20_000);
		unseenMs = 10_000;

		tickSeconds(60);

		assertEquals(List.of(30, 15, 6), spellCountdowns());
		verify(stars, times(3)).drop(any(), eq(1), eq(WantedCause.EVASION));
		assertEquals(List.of(Ending.HUNKER_DOWN, Ending.HUNKER_DOWN, Ending.HUNKER_DOWN), endings());
	}

	@Test
	@DisplayName("AUTO: running out of the zone is a clean break, half the stars rounded up")
	void auto_outsideTheZone_cleanBreakDropsHalfRoundedUp() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		playerAt = new Location(world, 500, 64, 0);
		unseenMs = 10_000;

		tickSeconds(16);

		verify(stars).drop(any(), eq(2), eq(WantedCause.EVASION));
		assertEquals(List.of(Ending.CLEAN_BREAK), endings());
	}

	@Test
	@DisplayName("AUTO: a long teleport during the spell rules out the clean break")
	void auto_teleported_stopsTheCleanBreak() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		playerAt = new Location(world, 500, 64, 0);
		unseenMs = 10_000;
		tickSeconds(1);

		clock.teleported(player);
		tickSeconds(15);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
		assertEquals(List.of(Ending.HUNKER_DOWN), endings());
	}

	@Test
	@DisplayName("AUTO: being seen again resets the momentum steps but not the chase arc, which counts a respot")
	void auto_seenAgain_resetsTheSteps_butNotTheArc() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		unseenMs = 10_000;
		tickSeconds(31);
		assertEquals(2, wanted.getLevel());

		unseenMs = 100;
		tickSeconds(1);
		unseenMs = 10_000;
		tickSeconds(1);

		// a full 20 s at two stars, not 20 x 0.75
		assertEquals(List.of(30, 20), spellCountdowns());
		assertEquals(1, arcs.arc(id).respots);
	}

	@Test
	@DisplayName("AUTO: being seen again resets the outside time and the teleport flag")
	void auto_seenAgain_resetsTheOutsideTimeAndTheTeleport() {
		// 60 s at two stars so a spell can sit outside a long while without finishing
		auto(List.of(10, 60, 30, 45, 60));
		arcs.start(id, WantedCause.CRIME, 2);
		playerAt = new Location(world, 500, 64, 0);
		unseenMs = 10_000;
		tickSeconds(1);
		clock.teleported(player);
		tickSeconds(25);

		unseenMs = 100;
		tickSeconds(1);
		unseenMs = 10_000;
		playerAt = new Location(world, 5, 64, 5);
		tickSeconds(31);
		playerAt = new Location(world, 500, 64, 0);
		tickSeconds(15);

		// fresh spell: 30 s inside, 15 s outside = 60 s of progress, ratio 0.33. Carried over it would be 40 / 70
		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
		assertEquals(List.of(Ending.HUNKER_DOWN), endings());

		// and the teleport did not survive the sighting: a new spell spent outside is a clean break
		unseenMs = 100;
		tickSeconds(1);
		unseenMs = 10_000;
		tickSeconds(6);
		assertEquals(List.of(Ending.HUNKER_DOWN, Ending.CLEAN_BREAK), endings());
	}

	@Test
	@DisplayName("a squad that went RETURNING and comes back to see him again counts one respot")
	void returningSquad_comesBackAndSees_countsOneRespot() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 10_000;
		tickSeconds(2);

		List<CopNpc> walkingHome = List.of(cop(CopState.RETURNING));
		when(group.getCops()).thenReturn(walkingHome);
		tickSeconds(1);
		assertNull(clock.snapshot(id));

		List<CopNpc> back = List.of(cop(CopState.PURSUING));
		when(group.getCops()).thenReturn(back);
		unseenMs = 100;
		tickSeconds(1);

		assertEquals(1, arcs.arc(id).respots);
	}

	@Test
	@DisplayName("AUTO: a narrow escape (20 s in sight) steps the next timer by 0.5 when the chase is not locked")
	void auto_narrowEscape_notLocked_stepsByHalf() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		unseenMs = 100;
		tickSeconds(21);

		unseenMs = 10_000;
		tickSeconds(32);

		assertEquals(List.of(30, 10), spellCountdowns());
		assertEquals(AutoDropPlanner.REASON_NARROW, plansAtDrop.get(0).reason());
	}

	@Test
	@DisplayName("AUTO: a locked chase keeps ONE_STAR timing, narrow escape or not")
	void auto_locked_keepsOneStarTiming_narrowOrNot() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.RESTORE, 3); // a restored chase counts as a logout: locked for 180 s
		unseenMs = 100;
		tickSeconds(21);

		unseenMs = 10_000;
		tickSeconds(70);

		assertEquals(List.of(30, 20, 10), spellCountdowns());
		assertEquals(List.of(Ending.STILL_HOT, Ending.STILL_HOT, Ending.STILL_HOT), endings());
		assertEquals(AutoDropPlanner.REASON_LOGOUT, plansAtDrop.get(0).reason());
	}

	@Test
	@DisplayName("AUTO: a level raised mid-search recomputes the timer for the new level")
	void auto_levelRaisedMidSearch_recomputesTheTimer() {
		auto();
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 10_000;
		tickSeconds(6);

		wanted.setLevel(3);
		tickSeconds(20);
		verify(stars, never()).drop(any(), anyInt(), any());

		tickSeconds(5);
		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("a reload that flips ONE_STAR to AUTO mid-search drops nothing on the next tick")
	void reloadFlipToAuto_midSearch_dropsNothingOnTheNextTick() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 10_000;
		tickSeconds(5);

		auto();
		tickSeconds(1);
		verify(stars, never()).drop(any(), anyInt(), any());

		tickSeconds(15);
		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("AUTO: a cancelled drop leaves the momentum steps alone")
	void auto_cancelledDrop_leavesTheStepsAlone() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		boolean[] cancelled = {false};
		when(stars.drop(any(), anyInt(), any())).thenAnswer(inv -> {
			if (!cancelled[0]) {
				cancelled[0] = true;
				return 0;
			}
			int n = inv.getArgument(1);
			wanted.setLevel(wanted.getLevel() - n);
			return n;
		});
		unseenMs = 10_000;

		tickSeconds(62);

		verify(stars, times(2)).drop(any(), eq(1), eq(WantedCause.EVASION));
		assertEquals(List.of(30, 15), spellCountdowns());
	}

	@Test
	@DisplayName("AUTO: a planner failure falls back to ONE_STAR, one star and today's timer, and the chase goes on")
	void auto_plannerFailure_dropsOneStar() {
		auto();
		arcs.start(id, WantedCause.CRIME, 2);
		crimeAt(now[0] - 25_000);
		when(learner.delta(id)).thenThrow(new IllegalStateException("boom"));
		unseenMs = 10_000;

		tickSeconds(32);

		// PETTY would have taken both stars; the fallback takes one, then the next one 10 s later
		verify(stars, times(2)).drop(any(), eq(1), eq(WantedCause.EVASION));
		assertEquals(List.of(20, 10), spellCountdowns());
	}

	@Test
	@DisplayName("ONE_STAR reads no heat ledger, no learner and plans nothing")
	void oneStar_readsNoLedgerNoLearner_andPlansNothing() {
		arcs.start(id, WantedCause.CRIME, 2);
		crimeAt(now[0] - 25_000);
		unseenMs = 10_000;

		tickSeconds(32);

		verify(stars, times(2)).drop(any(), eq(1), eq(WantedCause.EVASION));
		verifyNoInteractions(ledger, learner);
		verify(arcs, never()).view(any(), any(), any());
		verify(arcs, never()).stashPending(any(), any());
	}
}
