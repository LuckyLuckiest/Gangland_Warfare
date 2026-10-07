package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLearner;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseRecord;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChaseArcListener")
class ChaseArcListenerTest {

	private final UUID playerId = UUID.randomUUID();

	private Player            player;
	private Location          location;
	private Wanted            wanted;
	private AtomicLong        clock;
	private ChaseArcs         arcs;
	private ChaseLearner      learner;
	private ChaseConfigLoader config;
	private HeatLedger        ledger;
	private ChaseArcListener  listener;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getLocation()).thenReturn(location);
		when(location.clone()).thenReturn(location);

		wanted = new Wanted(null, 1, 5);
		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		config = mock(ChaseConfigLoader.class);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);

		clock   = new AtomicLong(1_000_000L);
		arcs    = new ChaseArcs(clock::get);
		learner = mock(ChaseLearner.class);
		ledger  = new HeatLedger(config, users, mock(CrimeService.class), p -> false, l -> false, () -> 10,
		                         clock::get);

		listener = new ChaseArcListener(arcs, learner, ledger, config);
	}

	private CrimeCommittedEvent crime(String id) {
		return new CrimeCommittedEvent(player, id, location, false, 0);
	}

	private AutoDrop.ChaseView view() {
		return arcs.view(playerId, ledger.chaseCrimes(playerId), ChaseConfig.DEFAULT.evasion().auto());
	}

	private void start(WantedCause cause, int level) {
		listener.onStart(new WantedStartEvent(player, wanted, level, cause));
	}

	private static EventPriority priorityOf(Class<?> type, String method, Class<?> param) throws Exception {
		return type.getMethod(method, param).getAnnotation(EventHandler.class).priority();
	}

	@Test
	@DisplayName("the end handler is HIGH and HeatListener.onChaseEnd is MONITOR, so the arc reads the crimes first")
	void endHandler_runsBeforeHeatListenerClears() throws Exception {
		assertEquals(EventPriority.HIGH, priorityOf(ChaseArcListener.class, "onChaseEnd", WantedEndEvent.class));
		assertEquals(EventPriority.MONITOR, priorityOf(HeatListener.class, "onChaseEnd", WantedEndEvent.class));
	}

	@Test
	@DisplayName("the end handler reads the crimes from the real ledger before HeatListener clears them")
	void chaseEnd_recordsTheChase_andRemembersACrimeChase() {
		start(WantedCause.CRIME, 2);
		ledger.record(crime(Crimes.KILL_COP));
		clock.addAndGet(40_000);
		arcs.lost(playerId);
		clock.addAndGet(20_000);

		listener.onChaseEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));
		new HeatListener(ledger).onChaseEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));

		ArgumentCaptor<ChaseRecord> captor = ArgumentCaptor.forClass(ChaseRecord.class);
		verify(learner).record(captor.capture(), anyLong());
		ChaseRecord record = captor.getValue();
		assertEquals(WantedCause.CRIME, record.startCause());
		assertEquals(WantedCause.EVASION, record.endCause());
		assertEquals(2, record.peak());
		assertEquals(60_000, record.chaseMs());
		assertEquals(40_000, record.contactMs());
		assertFalse(arcs.has(playerId));
		assertEquals(1, arcs.recentEnds(playerId, 30));
	}

	@Test
	@DisplayName("an admin chase with no crimes ends without a trace in recent")
	void chaseEnd_withNoCrime_leavesNoRecent() {
		start(WantedCause.ADMIN, 3);

		listener.onChaseEnd(new WantedEndEvent(player, wanted, WantedCause.ADMIN));

		assertEquals(0, arcs.recentEnds(playerId, 30));
	}

	@Test
	@DisplayName("an end with no arc records nothing")
	void chaseEnd_withNoArc_doesNothing() {
		listener.onChaseEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));

		verify(learner, never()).record(any(), anyLong());
	}

	@Test
	@DisplayName("a RESTORE level change is ignored: the 0->N change, then the RESTORE start, leaves quiet >= 0")
	void restoreLevelChange_isIgnored() {
		start(WantedCause.CRIME, 2);
		clock.addAndGet(1_000);
		listener.onQuit(new PlayerQuitEvent(player, "bye"));
		clock.addAndGet(600_000);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 2, WantedCause.RESTORE));
		start(WantedCause.RESTORE, 2);
		clock.addAndGet(500);

		assertTrue(view().quietMs() >= 0);
		assertEquals(1_500, view().chaseMs());
		assertEquals(1, view().quits());
	}

	@Test
	@DisplayName("a raise sets peak and resets quiet; a drop does neither")
	void levelChange_raiseIsHot_dropIsNot() {
		start(WantedCause.CRIME, 1);
		clock.addAndGet(10_000);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 3, WantedCause.CRIME));
		assertEquals(3, view().peak());
		assertEquals(0, view().quietMs());

		clock.addAndGet(10_000);
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 3, 2, WantedCause.EVASION));
		assertEquals(3, view().peak());
		assertEquals(10_000, view().quietMs());
	}

	@Test
	@DisplayName("a kept crime resets quiet; a weight-0 crime or heat off does not")
	void crime_resetsQuietOnlyWhenTheLedgerKeepsIt() {
		start(WantedCause.CRIME, 1);

		clock.addAndGet(5_000);
		listener.onCrime(crime(Crimes.KILL_COP));
		assertEquals(0, view().quietMs());

		clock.addAndGet(5_000);
		listener.onCrime(crime("not_a_weighted_crime"));
		assertEquals(5_000, view().quietMs());

		HeatSettings d = HeatSettings.DEFAULT;
		HeatSettings off = new HeatSettings(false, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                    d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights());
		when(config.get()).thenReturn(new ChaseConfig(off, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
		listener.onCrime(crime(Crimes.KILL_COP));
		assertEquals(5_000, view().quietMs());
	}

	@Test
	@DisplayName("a quit with an arc counts even though no UserManager lookup exists (as after RemoveAccountListener)")
	void quit_withAnArc_counts() {
		start(WantedCause.CRIME, 2);

		listener.onQuit(new PlayerQuitEvent(player, "bye"));

		assertEquals(1, view().quits());
	}

	@Test
	@DisplayName("a quit with no arc does nothing")
	void quit_withNoArc_doesNothing() {
		listener.onQuit(new PlayerQuitEvent(player, "bye"));

		assertFalse(arcs.has(playerId));
	}

	@Test
	@DisplayName("a rejoin after 30+ minutes offline gets a fresh RESTORE arc even when no other chase started")
	void restoreStart_afterLongOffline_prunesFirst() {
		start(WantedCause.CRIME, 2);
		clock.addAndGet(200_000);
		listener.onQuit(new PlayerQuitEvent(player, "bye"));
		clock.addAndGet(31 * 60_000L);

		start(WantedCause.RESTORE, 2);

		assertEquals(WantedCause.RESTORE, arcs.arc(playerId).startCause());
		assertEquals(1, view().quits());
		assertEquals(0, view().quietMs());
		assertEquals(0, view().chaseMs());
	}

	@Test
	@DisplayName("a RESTORE start with a live arc restores it; without one it starts a fresh arc with quits = 1")
	void restoreStart_restoresOrStarts() {
		start(WantedCause.RESTORE, 3);
		assertEquals(1, view().quits());
		assertEquals(3, view().peak());
	}
}
