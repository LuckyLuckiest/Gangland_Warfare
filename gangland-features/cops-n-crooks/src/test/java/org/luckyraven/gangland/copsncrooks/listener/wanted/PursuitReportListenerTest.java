package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.copsncrooks.wanted.report.BreakingNews;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-REPORT-1..5 at the event level: counters, the report at WantedEndEvent NORMAL, the star trigger, the closing headline. */
@DisplayName("PursuitReportListener")
class PursuitReportListenerTest {

	@TempDir
	Path tempDir;

	private final UUID playerId = UUID.randomUUID();
	private final UUID otherId  = UUID.randomUUID();

	private AtomicLong         clock;
	private ChaseArcs          arcs;
	private ChaseConfigLoader  config;
	private HeatLedger         ledger;
	private DetainmentRegistry registry;
	private Map<UUID, DetainedPlayer> rows;
	private List<String>       broadcasts;
	private Player             player;
	private Player             other;
	private Wanted             wanted;
	private PursuitReportListener listener;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		clock  = new AtomicLong(1_000_000L);
		arcs   = new ChaseArcs(clock::get);
		config = mock(ChaseConfigLoader.class);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);
		report(PursuitReportSettings.DEFAULT);

		ledger = mock(HeatLedger.class);
		when(ledger.chaseCrimes(playerId)).thenReturn(List.of());

		rows     = new ConcurrentHashMap<>();
		registry = mock(DetainmentRegistry.class);
		when(registry.getDetainedPlayers()).thenReturn(rows);

		WantedMessages messages = HudFixtures.messages(tempDir);
		broadcasts = new ArrayList<>();
		BreakingNews news = new BreakingNews(arcs, config, messages, broadcasts::add);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Steve");
		other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(otherId);
		wanted = new Wanted(null, 1, 5);

		listener = new PursuitReportListener(arcs, ledger, registry, config, messages, news);
	}

	private void report(PursuitReportSettings s) {
		when(config.getPursuitReport()).thenReturn(s);
	}

	private void crimes(double... heats) {
		List<CrimeRecord> list = new ArrayList<>();
		for (double heat : heats)
			list.add(new CrimeRecord("Assault", heat, clock.get(), mock(Location.class)));
		when(ledger.chaseCrimes(playerId)).thenReturn(list);
	}

	private void end(WantedCause cause) {
		listener.onChaseEnd(new WantedEndEvent(player, wanted, cause));
	}

	private List<String> sentToPlayer() {
		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(lines.capture());
		return lines.getAllValues().stream().map(l -> l.replaceAll("§.", "")).toList();
	}

	private CopNpc cop() {
		return mock(CopNpc.class);
	}

	@Test
	@DisplayName("the report handler runs at NORMAL, before ChaseArcListener (HIGH) ends the arc and HeatListener (MONITOR) clears the ledger")
	void reportHandlerIsNormalPriority() throws NoSuchMethodException {
		EventHandler handler = PursuitReportListener.class.getMethod("onChaseEnd", WantedEndEvent.class)
		                                                  .getAnnotation(EventHandler.class);

		assertEquals(EventPriority.NORMAL, handler.priority());
	}

	@Test
	@DisplayName("the counters and the star trigger run at MONITOR")
	void counterHandlersAreMonitor() throws NoSuchMethodException {
		assertEquals(EventPriority.MONITOR, PursuitReportListener.class.getMethod("onCopDeath", CopDeathEvent.class)
		                                                               .getAnnotation(EventHandler.class).priority());
		assertEquals(EventPriority.MONITOR, PursuitReportListener.class.getMethod("onCuffed", CuffedEvent.class)
		                                                               .getAnnotation(EventHandler.class).priority());
		assertEquals(EventPriority.MONITOR,
		             PursuitReportListener.class.getMethod("onLevelChange", WantedLevelChangeEvent.class)
		                                        .getAnnotation(EventHandler.class).priority());
	}

	@Test
	@DisplayName("the end of an evasion chase prints the report, with the ledger's crime heat still in it")
	void reportOnEnd_beforeLedgerClear() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		crimes(100, 50);

		end(WantedCause.EVASION);

		List<String> lines = sentToPlayer();
		assertEquals(7, lines.size());
		assertTrue(lines.get(0).contains("PURSUIT REPORT"), lines.get(0));
		assertTrue(lines.get(1).contains("Escaped"), lines.get(1));
		// 150 heat x 10, no cops down
		assertTrue(lines.get(6).replaceAll("[,. ]", "").contains("1500"), lines.get(6));
	}

	@Test
	@DisplayName("a rejoin (RESTORE) never reports, and leaves the arc alone")
	void restoreNeverReports() {
		arcs.start(playerId, WantedCause.CRIME, 3);

		end(WantedCause.RESTORE);

		verify(player, never()).sendMessage(anyString());
		assertTrue(broadcasts.isEmpty());
	}

	@Test
	@DisplayName("an end with no arc (restart, or a stale end) prints nothing")
	void noArc_noReport() {
		end(WantedCause.EVASION);

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a chase whose peak is below Min_Peak_Stars prints nothing")
	void belowMinPeak_noReport() {
		report(new PursuitReportSettings(true, 2, 2500.0, 10.0, true, 4, 120, 300));
		arcs.start(playerId, WantedCause.CRIME, 1);

		end(WantedCause.EVASION);

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("Pursuit_Report.Enable false prints nothing")
	void reportDisabled_noReport() {
		report(new PursuitReportSettings(false, 1, 2500.0, 10.0, true, 4, 120, 300));
		arcs.start(playerId, WantedCause.CRIME, 3);

		end(WantedCause.EVASION);

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("0.21.2 T-545: a silent cop death counts once, when dispatch notices it and re-fires it, not silent")
	void silentDeath_notCounted() {
		arcs.start(playerId, WantedCause.CRIME, 3);

		listener.onCopDeath(new CopDeathEvent(cop(), player, true, null));
		assertEquals(0, arcs.arc(playerId).copsDown());

		listener.onCopDeath(new CopDeathEvent(cop(), player, false, null));
		assertEquals(1, arcs.arc(playerId).copsDown());
	}

	@Test
	@DisplayName("cop kills by the suspect and cuffs of the suspect are counted; a cop killed by someone else, or by no one, is not")
	void countsCopKillsAndCuffs() {
		arcs.start(playerId, WantedCause.CRIME, 3);

		listener.onCopDeath(new CopDeathEvent(cop(), player));
		listener.onCopDeath(new CopDeathEvent(cop(), player));
		listener.onCopDeath(new CopDeathEvent(cop(), other));
		listener.onCopDeath(new CopDeathEvent(cop(), null));
		listener.onCuffed(new CuffedEvent(cop(), player, 2.0, 3));
		listener.onCuffed(new CuffedEvent(cop(), other, 2.0, 3));

		assertEquals(2, arcs.arc(playerId).copsDown());
		assertEquals(1, arcs.arc(playerId).busts());
		end(WantedCause.DEATH);
		List<String> lines = sentToPlayer();
		assertTrue(lines.get(4).endsWith("2"), lines.get(4));
		assertTrue(lines.get(5).endsWith("1"), lines.get(5));
	}

	@Test
	@DisplayName("a kill or a cuff with no arc is ignored without error")
	void countersWithoutArc_noError() {
		listener.onCopDeath(new CopDeathEvent(cop(), player));
		listener.onCuffed(new CuffedEvent(cop(), player, 2.0, 3));

		assertFalse(arcs.has(playerId));
	}

	@Test
	@DisplayName("ARREST reads the registry row's origin: BUSTED_DOWNED -> Busted")
	void arrestOriginEnding() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		DetainedPlayer row = new DetainedPlayer(playerId, null, DetainmentState.HANDCUFFED);
		row.setOrigin(ArrestOrigin.BUSTED_DOWNED);
		rows.put(playerId, row);

		end(WantedCause.ARREST);

		assertTrue(sentToPlayer().get(1).contains("Busted"));
	}

	@Test
	@DisplayName("ARREST with no registry row reads Arrested (a null origin is CUFFED)")
	void arrestNoRow_isArrested() {
		arcs.start(playerId, WantedCause.CRIME, 3);

		end(WantedCause.ARREST);

		assertTrue(sentToPlayer().get(1).contains("Arrested"));
	}

	@Test
	@DisplayName("a stale registry row does not colour a non-ARREST ending")
	void staleRowIgnoredOutsideArrest() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		DetainedPlayer row = new DetainedPlayer(playerId, null, DetainmentState.HANDCUFFED);
		row.setOrigin(ArrestOrigin.SURRENDERED);
		rows.put(playerId, row);

		end(WantedCause.DEATH);

		assertTrue(sentToPlayer().get(1).contains("Wasted"));
	}

	@Test
	@DisplayName("a level change to 4 stars (MONITOR, new level) makes the live news once")
	void levelChange_triggersLiveNews_withNewLevel() {
		arcs.start(playerId, WantedCause.CRIME, 1);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 4, WantedCause.CRIME));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 4, 5, WantedCause.CRIME));

		assertEquals(1, broadcasts.size());
		assertTrue(arcs.arc(playerId).newsSent());
	}

	@Test
	@DisplayName("a level change below Min_Stars makes no news")
	void levelChange_belowMin_noNews() {
		arcs.start(playerId, WantedCause.CRIME, 1);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 3, WantedCause.CRIME));

		assertTrue(broadcasts.isEmpty());
	}

	@Test
	@DisplayName("the end of a chase that made the news broadcasts the closing headline with its ending")
	void endOfNewsChase_broadcastsClosing() {
		arcs.start(playerId, WantedCause.CRIME, 4);
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 4, WantedCause.CRIME));
		broadcasts.clear();

		end(WantedCause.DEATH);

		assertEquals(1, broadcasts.size());
		assertTrue(broadcasts.get(0).contains("Wasted"), broadcasts.get(0));
	}

	@Test
	@DisplayName("the end of a quiet chase broadcasts nothing")
	void endOfQuietChase_noBroadcast() {
		arcs.start(playerId, WantedCause.CRIME, 2);

		end(WantedCause.EVASION);

		assertTrue(broadcasts.isEmpty());
	}

	@Test
	@DisplayName("the closing headline is not lost when the report itself is off or below Min_Peak_Stars")
	void closingHeadline_independentOfReportSwitches() {
		arcs.start(playerId, WantedCause.CRIME, 4);
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 4, WantedCause.CRIME));
		broadcasts.clear();
		report(new PursuitReportSettings(false, 5, 2500.0, 10.0, true, 4, 120, 300));

		end(WantedCause.EVASION);

		verify(player, never()).sendMessage(anyString());
		assertEquals(1, broadcasts.size());
	}

	@Test
	@DisplayName("a RESTORE end broadcasts no closing headline even for a news chase")
	void restoreEnd_noClosing() {
		arcs.start(playerId, WantedCause.CRIME, 4);
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 4, WantedCause.CRIME));
		broadcasts.clear();

		end(WantedCause.RESTORE);

		assertTrue(broadcasts.isEmpty());
	}

	@Test
	@DisplayName("the RESTORE 0->N level change never makes news: the offline gap is not chase time (P-REPORT-3/4)")
	void restoreLevelChange_offlineTimeDoesNotMakeNews() {
		arcs.start(playerId, WantedCause.CRIME, 1);
		clock.addAndGet(30_000L);
		arcs.quit(playerId);
		clock.addAndGet(120_000L);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 1, WantedCause.RESTORE));

		assertTrue(broadcasts.isEmpty());
	}

	@Test
	@DisplayName("the ending N->0 level change never makes live news as the chase ends")
	void endingLevelChange_noLiveNewsAsChaseEnds() {
		arcs.start(playerId, WantedCause.CRIME, 1);
		clock.addAndGet(130_000L);

		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		assertTrue(broadcasts.isEmpty());
	}
}
