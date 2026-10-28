package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HideoutSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.QuietSpeedSettings;
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
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
	private Hideouts          hideouts;
	private QuietTrail        quiet;
	private List<CopNpc>      hunters;
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
		hunters = new ArrayList<>(List.of(cop(CopState.PURSUING)));
		when(group.getCops()).thenAnswer(inv -> hunters);
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

		// every existing case keeps speed 1.0: no hideout, no cold trail
		hideouts = mock(Hideouts.class);
		quiet    = mock(QuietTrail.class);
		when(quiet.speed(any(), anyLong())).thenReturn(1.0);

		clock = new EvasionClock(config, copManager, detainment, stars, users, ledger, arcs, learner, hideouts, quiet,
		                         () -> now[0], events::add);
	}

	/** A RETURNING cop that still targets him, as a leashed cop does: it never clears its target. */
	private CopNpc walker() {
		CopNpc cop = cop(CopState.RETURNING);
		when(cop.getTargetPlayerId()).thenReturn(id);
		return cop;
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

	private EvasionClock clockWithTracker(java.util.function.Predicate<Player> trackerLive) {
		return new EvasionClock(config, copManager, detainment, stars, users(), ledger, arcs, learner, hideouts, quiet,
		                        () -> now[0], events::add, trackerLive);
	}

	@SuppressWarnings("unchecked")
	private UserManager<Player> users() {
		UserManager<Player> users = mock(UserManager.class);
		User<Player>        user  = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		when(users.getUser(player)).thenReturn(user);
		return users;
	}

	@Test
	@DisplayName("C-24 / CQ-TRK-1: while a tracker tag is live the clock holds, no stars decay past Lost_Sight_Seconds")
	void liveTrackerTag_holdsTheClock() {
		unseenMs = 60_000;
		EvasionClock tagged = clockWithTracker(p -> true);

		for (int i = 0; i < 90; i++) {
			now[0] += 1000;
			tagged.tick(player, group);
		}

		verify(stars, never()).drop(any(), anyInt(), any());
		assertEquals(2, wanted.getLevel());
	}

	@Test
	@DisplayName("C-24: once the tag has expired the normal decay runs and takes a star")
	void expiredTrackerTag_decaysNormally() {
		unseenMs = 60_000;
		boolean[] live   = {true};
		EvasionClock tagged = clockWithTracker(p -> live[0]);

		for (int i = 0; i < 30; i++) {
			now[0] += 1000;
			tagged.tick(player, group);
		}
		live[0] = false;
		for (int i = 0; i < 60; i++) {
			now[0] += 1000;
			tagged.tick(player, group);
		}

		verify(stars, org.mockito.Mockito.atLeastOnce()).drop(any(), anyInt(), eq(WantedCause.EVASION));
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
	@DisplayName("T-537: only returning cops: the clock still handles decay and the search runs on (no OFF)")
	void onlyReturningCops_stillHandlesDecay_andKeepsSearching() {
		unseenMs = 10_000;
		tickSeconds(1);
		assertTrue(clock.handlesDecay(player, wanted));

		// a real walker keeps hunting him (targetPlayerId stays set when it leashes); inside the range it is no refill
		List<CopNpc> walkingHome = List.of(walker());
		when(group.getCops()).thenReturn(walkingHome);
		assertTrue(clock.handlesDecay(player, wanted), "while the module hunts him the Repeating_Timer takes nothing");
		tickSeconds(1);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertFalse(stateNames().contains(EvasionState.OFF));
	}

	@Test
	@DisplayName("a whole-squad leash pauses the search: walkers still hunting him keep the track and its progress (cause E)")
	void leashedSquad_stillHuntingHim_pausesInsteadOfClearing() {
		unseenMs = 10_000;
		tickSeconds(5);
		EvasionSnapshot before = clock.snapshot(id);
		assertEquals(EvasionState.SEARCHING, before.state());

		CopNpc walker = walker();
		when(copManager.isStranded(walker, player)).thenReturn(true); // past Pursuit.Max_Distance: the refill is coming
		when(group.getCops()).thenReturn(List.of(walker));
		tickSeconds(10);

		assertTrue(clock.handlesDecay(player, wanted));
		EvasionSnapshot paused = clock.snapshot(id);
		assertNotNull(paused, "not cleared");
		assertEquals(before.secondsLeft(), paused.secondsLeft(), "paused: no progress while nobody stands");
		assertFalse(stateNames().contains(EvasionState.OFF));
	}

	@Test
	@DisplayName("T-537/T-528: a returning walker inside Pursuit.Max_Distance holds nothing: the search keeps its progress at No_Cop_Inside_Speed")
	void nearWalker_targetingHim_searchRunsAtNoCopSpeed() {
		unseenMs = 10_000;
		tickSeconds(5);
		assertEquals(16, clock.snapshot(id).secondsLeft()); // 4 s of 20 counted at full speed
		CopNpc near = walker();
		when(copManager.isStranded(near, player)).thenReturn(false); // the spawn task does not refill it
		when(group.getCops()).thenReturn(List.of(near));

		assertTrue(clock.handlesDecay(player, wanted), "no Repeating_Timer decay while hunted");
		tickSeconds(1);
		// 4.25 s of 20 counted; the 15.75 s left run at 0.25 inside the zone
		assertEquals(63, clock.snapshot(id).secondsLeft());
		assertFalse(stateNames().contains(EvasionState.OFF));
	}

	@Test
	@DisplayName("an active hand-off bias with every cop gone pauses the search instead of clearing it (cause E)")
	void activeBias_noCopsLeft_pausesInsteadOfClearing() {
		unseenMs = 10_000;
		tickSeconds(5);
		when(group.getCops()).thenReturn(List.of());
		when(group.biasAt(anyLong())).thenReturn(new SpawnBias(new Vector(1, 0, 0), playerAt, Long.MAX_VALUE, 60.0));

		tickSeconds(3);

		assertNotNull(clock.snapshot(id));
		assertFalse(stateNames().contains(EvasionState.OFF));
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
	@DisplayName("T-537: a SEARCHING player whose cops are all dead keeps his search and the clock keeps the decay: killing every cop clears nothing")
	void emptyGroup_whileSearching_keepsSearching() {
		unseenMs = 10_000;
		tickSeconds(2);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());

		when(group.getCops()).thenReturn(List.of());
		tickSeconds(3);

		assertFalse(stateNames().contains(EvasionState.OFF));
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertTrue(clock.handlesDecay(player, wanted));
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("T-537 (B4): while a group exists the clock owns the decay, cops or none; without a group the old timer does")
	void handlesDecay_trueWhileGroupExists() {
		hunters.clear();

		assertTrue(clock.handlesDecay(player, wanted));
		when(copManager.groupOf(id)).thenReturn(null);
		assertFalse(clock.handlesDecay(player, wanted));
	}

	@Test
	@DisplayName("T-537: no unit ever stands: once the first response lapses he is searched at the last-known spot and escapes by EVASION")
	void noUnitsEverSpawn_searchesLastKnown_dropsAsEvasion() {
		hunters.clear();
		unseenMs = 10_000;

		tickSeconds(1);
		EvasionSnapshot search = clock.snapshot(id);
		assertEquals(EvasionState.SEARCHING, search.state());
		assertEquals(centre, search.zoneCentre(), "the squad's last-known spot, not where he is");

		// level 2: 20 s inside the zone at No_Cop_Inside_Speed 0.25 = 80 s
		tickSeconds(79);
		verify(stars, never()).drop(any(), anyInt(), any());
		tickSeconds(1);
		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("T-537: a rejoin before the spawn task queued a unit (no tip, no unit yet) holds for the rejoin grace: no search ring on his live spot")
	void restoreStart_beforeAUnitIsQueued_holdsForTheRejoinGrace() {
		hunters.clear();
		unseenMs = 10_000;
		when(squad.lastKnownLocation()).thenReturn(null); // a RESTORE start tips nothing (R4)
		long graceEnd = now[0] + 15_000;
		when(group.getBreatherUntil()).thenAnswer(inv -> graceEnd);

		tickSeconds(3);

		assertNull(clock.snapshot(id));
		assertFalse(stateNames().contains(EvasionState.SEARCHING));
	}

	@Test
	@DisplayName("T-537: no cop stands and he is outside the zone: Outside_Zone_Speed applies, as with cops")
	void noCop_outsideTheZone_runsAtOutsideSpeed() {
		hunters.clear();
		unseenMs = 10_000;
		playerAt = new Location(world, 100, 64, 100);

		tickSeconds(1);

		assertEquals(10, clock.snapshot(id).secondsLeft()); // 20 s at 2.0
	}

	@Test
	@DisplayName("T-537: No_Cop_Inside_Speed 0 is floored at 0.01: the clock still moves, nothing gets stuck")
	void noCopInsideSpeedZero_isFlooredNotFrozen() {
		EvasionSettings d = EvasionSettings.DEFAULT;
		settings = new EvasionSettings(true, 3, DropMode.ONE_STAR, d.searchRadius(), d.secondsToDrop(), 2.0, d.auto(),
		                               d.hideout(), d.quietSpeed(), d.maxSpeed(), 0.0);
		hunters.clear();
		unseenMs = 10_000;

		tickSeconds(1);
		assertEquals(2000, clock.snapshot(id).secondsLeft());
		tickSeconds(1);
		assertEquals(1999, clock.snapshot(id).secondsLeft());
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
	@DisplayName("AUTO: a drop another plugin cancels leaves no stale plan on the arc to label a later drop")
	void auto_cancelledDrop_clearsThePendingPlan() {
		auto();
		arcs.start(id, WantedCause.CRIME, 2);
		crimeAt(now[0] - 25_000);
		crimeAt(now[0] - 15_000);
		when(stars.drop(any(), anyInt(), any())).thenReturn(0);
		unseenMs = 10_000;

		tickSeconds(21);

		verify(stars).drop(any(), eq(2), eq(WantedCause.EVASION));
		assertNull(arcs.takePending(id));
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

		List<CopNpc> walkingHome = List.of(walker());
		when(group.getCops()).thenReturn(walkingHome);
		tickSeconds(1);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state()); // T-537: no longer cleared

		List<CopNpc> back = List.of(cop(CopState.PURSUING));
		when(group.getCops()).thenReturn(back);
		unseenMs = 100;
		tickSeconds(1);

		assertEquals(1, arcs.arc(id).respots);
	}

	@Test
	@DisplayName("a replacement squad that never sees him starts a search but leaves lastLostAt at the last real loss")
	void replacementSquad_neverSeesHim_keepsLastLostAt() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(1);
		unseenMs = 10_000;
		tickSeconds(1);
		long lostAt = arcs.arc(id).lastLostAt();
		assertTrue(lostAt > 0);

		List<CopNpc> walkingHome = List.of(walker());
		when(group.getCops()).thenReturn(walkingHome);
		tickSeconds(1);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state()); // T-537: no longer cleared

		List<CopNpc> fresh = List.of(cop(CopState.PURSUING));
		when(group.getCops()).thenReturn(fresh);
		tickSeconds(1);

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(lostAt, arcs.arc(id).lastLostAt());
	}

	@Test
	@DisplayName("a squad wiped out while he is in sight ends the contact: lastLostAt is the wipe, not 0")
	void squadDiesWhileSeen_stampsLastLostAtAtTheWipe() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(5);
		assertEquals(0, arcs.arc(id).lastLostAt());

		CopNpc dead = cop(CopState.PURSUING);
		when(dead.isValid()).thenReturn(false);
		when(group.getCops()).thenReturn(List.of(dead));
		tickSeconds(1);
		long wipedAt = now[0];
		// T-537: a dead squad sees nothing: the search starts at the wipe instead of the track being cleared
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());

		List<CopNpc> fresh = List.of(cop(CopState.PURSUING));
		when(group.getCops()).thenReturn(fresh);
		unseenMs = Long.MAX_VALUE;
		tickSeconds(1);

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(wipedAt, arcs.arc(id).lastLostAt());
	}

	@Test
	@DisplayName("a quit while in sight stamps lastLostAt at the quit; a quit while searching keeps the earlier loss")
	void clearWhileSeen_stampsLost_clearWhileSearching_doesNot() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(3);
		clock.clear(player);
		long quitAt = now[0];
		assertEquals(quitAt, arcs.arc(id).lastLostAt());

		unseenMs = 10_000;
		tickSeconds(1);
		long searchingSince = arcs.arc(id).lastLostAt();
		now[0] += 1000;
		clock.clear(player);
		assertEquals(searchingSince, arcs.arc(id).lastLostAt());
	}

	/** Three ticks in which the squad's last sighting ages as a real squad's does; the third is the switch to SEARCHING. */
	private void loseSight() {
		long seenAt = now[0] - unseenMs;
		for (int k = 0; k < 3; k++) {
			now[0] += 1000;
			unseenMs = now[0] - seenAt;
			clock.tick(player, group);
		}
	}

	/** Quits while tracked and rejoins {@code awayMs} later: the RESTORE start's arc restore. */
	private void quitAndRejoin(long awayMs) {
		clock.clear(player);
		arcs.quit(id);
		now[0] += awayMs;
		arcs.restore(id);
	}

	@Test
	@DisplayName("a rejoin's seeded sighting is no contact: no respot, and lastLostAt keeps the loss before the quit")
	void restoreSeed_isNoRespot_andKeepsThePreQuitLoss() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(10);
		loseSight();
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		long lostAt = arcs.arc(id).lastLostAt();
		tickSeconds(5);

		quitAndRejoin(300_000);
		// CopManager.onWantedStart seeds a sighting at the rejoin; the first cop spawns a second later
		unseenMs = 0;
		loseSight();

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(0, arcs.arc(id).respots);
		assertEquals(lostAt + 300_000, arcs.arc(id).lastLostAt());
	}

	@Test
	@DisplayName("a cop that really sees him after a rejoin is contact: one respot, lastLostAt at the next loss")
	void restoreSeed_thenARealSighting_countsAsContact() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(10);
		loseSight();
		tickSeconds(5);

		quitAndRejoin(300_000);
		unseenMs = 1000;
		tickSeconds(1);
		assertEquals(0, arcs.arc(id).respots);
		unseenMs = 100;
		tickSeconds(1);
		assertEquals(1, arcs.arc(id).respots);
		loseSight();

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(now[0], arcs.arc(id).lastLostAt());
	}

	@Test
	@DisplayName("a rejoin's seeded sighting fires no SEEN (no cop has eyes on him); a real sighting after it does")
	void restoreSeed_firesNoSeen_untilACopReallySeesHim() {
		arcs.start(id, WantedCause.CRIME, 2);
		unseenMs = 100;
		tickSeconds(10);
		loseSight();
		tickSeconds(5);

		quitAndRejoin(300_000);
		events.clear();
		unseenMs = 1000;
		tickSeconds(1);
		assertFalse(stateNames().contains(EvasionState.SEEN), "the seed is no sighting: contacts stay open");

		unseenMs = 100;
		tickSeconds(1);
		assertEquals(List.of(EvasionState.SEEN), stateNames());
	}

	@Test
	@DisplayName("0.21.0 P-GET-5: the first real sighting (dispatch or crime scene counts) sets sighted")
	void sighted_firstRealSighting_sets() {
		arcs.start(id, WantedCause.CRIME, 2);
		assertFalse(arcs.arc(id).sighted());

		unseenMs = 100;
		tickSeconds(1);

		assertTrue(arcs.arc(id).sighted());
	}

	@Test
	@DisplayName("0.21.0 P-GET-5: a rejoin's seed alone leaves sighted false until a cop really sees him")
	void sighted_rejoinSeedOnly_staysFalse() {
		arcs.start(id, WantedCause.CRIME, 2);
		quitAndRejoin(300_000);
		unseenMs = 1000;
		tickSeconds(1);
		assertFalse(arcs.arc(id).sighted());

		unseenMs = 100;
		tickSeconds(1);
		assertTrue(arcs.arc(id).sighted());
	}

	@Test
	@DisplayName("AUTO: 17.9 s in sight plus the 3 s Lost_Sight_Seconds grace is not a narrow escape")
	void auto_lostSightGrace_isNotTimeInSight() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		unseenMs = 100;
		tickSeconds(18);
		loseSight();

		unseenMs = 10_000;
		tickSeconds(31);

		assertNotEquals(AutoDropPlanner.REASON_NARROW, plansAtDrop.get(0).reason());
	}

	@Test
	@DisplayName("AUTO: a narrow escape (20 s in sight) steps the next timer by 0.5 when the chase is not locked")
	void auto_narrowEscape_notLocked_stepsByHalf() {
		auto();
		wanted.setLevel(3);
		arcs.start(id, WantedCause.CRIME, 3);
		unseenMs = 100;
		tickSeconds(24);
		loseSight();

		unseenMs = 10_000;
		tickSeconds(31);

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
		verify(arcs, never()).view(any(), any(), any(), any());
		verify(arcs, never()).stashPending(any(), any());
	}

	// ---- 0.16.0: hideouts, cold trail, units en route ----

	/** The default settings with the given hideout, quiet speed and speed cap. */
	private static EvasionSettings with(HideoutSettings hideout, QuietSpeedSettings quietSpeed, double maxSpeed) {
		EvasionSettings d = EvasionSettings.DEFAULT;
		return new EvasionSettings(true, 3, DropMode.ONE_STAR, d.searchRadius(), d.secondsToDrop(), 2.0,
		                           AutoSettings.DEFAULT, hideout, quietSpeed, maxSpeed);
	}

	/** The player stands in the hideout {@code regionId}, open to him. */
	private void inHideout(String regionId) {
		PlaceRegion region = mock(PlaceRegion.class);
		when(region.id()).thenReturn(regionId);
		when(hideouts.at(player)).thenReturn(region);
	}

	@Test
	@DisplayName("inside a hideout he reached unseen the timer runs at Hideout.Speed: a two-star drop takes 10 s")
	void insideAHideoutReachedUnseen_countsAtHideoutSpeed() {
		unseenMs = 10_000;
		inHideout("copsncrooks:1");

		tickSeconds(10);
		verify(stars, never()).drop(any(), anyInt(), any());
		tickSeconds(1);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("the hideout that holds the last sighting never counts: he was seen walking in")
	void hideoutHoldingTheLastSighting_doesNotCount() {
		unseenMs = 10_000;
		inHideout("copsncrooks:1");
		when(hideouts.idAt(any(), eq(id))).thenReturn("copsncrooks:1");

		tickSeconds(20);
		verify(stars, never()).drop(any(), anyInt(), any());
		tickSeconds(1);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("a hideout Hideouts refuses him (a rival gang's) is open ground: speed 1")
	void rivalGangHideout_doesNotCount() {
		unseenMs = 10_000;
		when(hideouts.at(player)).thenReturn(null);

		tickSeconds(20);

		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("Hideout.Enable false makes a hideout open ground")
	void hideoutDisabled_speedOne() {
		settings = with(new HideoutSettings(false, 2.0), QuietSpeedSettings.DEFAULT, 4.0);
		unseenMs = 10_000;
		inHideout("copsncrooks:1");

		tickSeconds(20);

		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("two quiet minutes (the QuietTrail's 1.5) speed the timer: the first countdown reads 14 s, the drop lands on the 15th tick")
	void quietTwoMinutes_countsOneAndAHalfTimesFaster() {
		unseenMs = 10_000;
		when(quiet.speed(any(), anyLong())).thenReturn(1.5);

		tickSeconds(14);
		assertEquals(14, states().get(0).getSecondsLeft());
		verify(stars, never()).drop(any(), anyInt(), any());
		tickSeconds(1);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("a quiet factor under 1 is ignored: cold trail only ever speeds up")
	void quietFactorUnderOne_isFlooredAtOne() {
		unseenMs = 10_000;
		when(quiet.speed(any(), anyLong())).thenReturn(0.0);

		tickSeconds(21);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("outside 2 x hideout 2 x quiet 2 is capped at Max_Speed 4: the drop lands on the sixth tick, not the fourth")
	void speedsMultiply_andAreCappedAtMaxSpeed() {
		unseenMs = 10_000;
		playerAt = new Location(world, 500, 64, 0);
		inHideout("copsncrooks:1");
		when(quiet.speed(any(), anyLong())).thenReturn(2.0);

		tickSeconds(5);
		verify(stars, never()).drop(any(), anyInt(), any());
		tickSeconds(1);

		verify(stars).drop(any(), eq(1), eq(WantedCause.EVASION));
	}

	@Test
	@DisplayName("while a cop has him in sight no multiplier is read and nothing counts (characterization pin)")
	void seen_noMultiplierApplies() {
		unseenMs = 500;
		inHideout("copsncrooks:1");
		when(quiet.speed(any(), anyLong())).thenReturn(2.0);

		tickSeconds(60);

		verify(stars, never()).drop(any(), anyInt(), any());
		verify(hideouts, never()).at(any());
		assertEquals(EvasionState.SEEN, clock.snapshot(id).state());
	}

	@Test
	@DisplayName("units en route keep the stars (the clock owns decay) but the clock makes no progress until one stands in the world")
	void unitsEnRouteOnly_ownDecay_butMakeNoProgress() {
		hunters.clear();
		when(group.unitsEnRoute(anyLong())).thenReturn(true);
		unseenMs = 10_000;

		tickSeconds(30);

		assertTrue(clock.handlesDecay(player, wanted));
		assertEquals(2, wanted.getLevel());
		assertNull(clock.snapshot(id));
		assertTrue(events.isEmpty());
		verify(stars, never()).drop(any(), anyInt(), any());
	}

	@Test
	@DisplayName("T-537: after a wipe the search runs through the breather and the ETA at No_Cop_Inside_Speed, then at full speed with the first unit")
	void wipeAfterStood_searchRunsThroughBreather_insideSpeedQuarter() {
		when(group.hasStood()).thenReturn(true); // his squad stood, then was wiped: no first-response hold
		unseenMs = 10_000;
		tickSeconds(5);
		assertEquals(16, clock.snapshot(id).secondsLeft());
		CopNpc cop = hunters.remove(0);
		when(group.unitsEnRoute(anyLong())).thenReturn(true);

		tickSeconds(4);
		assertEquals(60, clock.snapshot(id).secondsLeft()); // 5 s counted, 15 s left at 0.25
		verify(stars, never()).drop(any(), anyInt(), any());

		hunters.add(cop);
		tickSeconds(1);

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
		assertEquals(14, clock.snapshot(id).secondsLeft());
	}

	@Test
	@DisplayName("a RESTORE start's grace and ETA pass with units queued: no search, no progress, the arc untouched; the first unit starts it")
	void restoreGrace_noProgressUntilAUnitSpawns() {
		arcs.start(id, WantedCause.RESTORE, 2);
		long lastHotAt = arcs.arc(id).lastHotAt;
		hunters.clear();
		when(group.unitsEnRoute(anyLong())).thenReturn(true);
		unseenMs = 10_000;

		tickSeconds(15 + 25);

		assertTrue(clock.handlesDecay(player, wanted));
		assertNull(clock.snapshot(id));
		assertEquals(1, arcs.arc(id).quits);
		assertEquals(lastHotAt, arcs.arc(id).lastHotAt);
		verify(stars, never()).drop(any(), anyInt(), any());

		hunters.add(cop(CopState.PURSUING));
		tickSeconds(1);

		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
	}

	@Test
	@DisplayName("T-537: a unit 11 s past its ETA stops holding the clock, but the clock keeps the decay: it searches instead")
	void stuckUnits_stopHolding_butTheClockKeepsTheDecay() {
		hunters.clear();
		unseenMs = 10_000;
		long arriveAt = now[0];
		when(group.unitsEnRoute(anyLong())).thenAnswer(inv -> arriveAt + 10_000 > inv.<Long>getArgument(0));

		now[0] = arriveAt + 9_000;
		assertTrue(clock.handlesDecay(player, wanted));
		clock.tick(player, group);
		assertNull(clock.snapshot(id), "the first response still holds");
		now[0] = arriveAt + 11_000;
		assertTrue(clock.handlesDecay(player, wanted));
		clock.tick(player, group);
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
	}

	@Test
	@DisplayName("a POSTED cop counts as live and the search runs (characterization pin)")
	void postedCop_countsAsLive() {
		hunters.clear();
		hunters.add(cop(CopState.POSTED));
		unseenMs = 10_000;

		tickSeconds(1);

		assertTrue(clock.handlesDecay(player, wanted));
		assertEquals(EvasionState.SEARCHING, clock.snapshot(id).state());
	}
}
