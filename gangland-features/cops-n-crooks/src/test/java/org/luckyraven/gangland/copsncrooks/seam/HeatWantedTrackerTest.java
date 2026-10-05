package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.combo.KillComboTracker;
import org.luckyraven.gangland.copsncrooks.events.combo.KillComboEvent;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the seam-3 delegate: the kill-combo forwards of the old tracker, and the heat routing of counted kills (cop and
 * player kills become crimes, civilian kills are published elsewhere, defending your own turf mints nothing).
 */
@DisplayName("HeatWantedTracker")
class HeatWantedTrackerTest {

	private KillCombo         killCombo;
	private NpcMarkManager    markManager;
	private HeatLedger        ledger;
	private CrimeService      crimes;
	private ChaseConfigLoader config;
	private boolean           defending;
	private Player            killer;
	private Wanted            wanted;
	private Location          at;
	private HeatWantedTracker tracker;
	private Boolean           savedCombo;
	private BukkitStatics     bukkit;

	@BeforeEach
	void setUp() throws ReflectiveOperationException {
		bukkit      = BukkitStatics.install();
		killCombo   = mock(KillCombo.class);
		markManager = mock(NpcMarkManager.class);
		ledger      = mock(HeatLedger.class);
		crimes      = mock(CrimeService.class);
		config      = mock(ChaseConfigLoader.class);
		when(config.get()).thenReturn(ChaseConfig.DEFAULT);

		killer = mock(Player.class);
		wanted = new Wanted(null, 1, 5);
		at     = mock(Location.class);

		BiPredicate<Player, Location> defend = (p, l) -> defending;
		tracker = new HeatWantedTracker(config, ledger, crimes, killCombo, markManager, defend);

		savedCombo = (Boolean) comboField().get(null);
		combo(true);
	}

	@AfterEach
	void tearDown() throws ReflectiveOperationException {
		bukkit.close();
		combo(savedCombo != null && savedCombo);
	}

	private static Field comboField() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("wantedKillComboEnabled");
		field.setAccessible(true);
		return field;
	}

	private static void combo(boolean enabled) throws ReflectiveOperationException {
		comboField().set(null, enabled);
	}

	private void heatOff() {
		HeatSettings d = HeatSettings.DEFAULT;
		HeatSettings off = new HeatSettings(false, d.starThresholds(), d.streakBonus(), d.seenByCopMultiplier(),
		                                    d.turfWarMultiplier(), d.assaultRepeatSeconds(), d.crimeWeights());
		when(config.get()).thenReturn(new ChaseConfig(off, ChaseConfig.DEFAULT.evasion(), ChaseConfig.DEFAULT.hud(),
		                                              ChaseConfig.DEFAULT.chargeSheet()));
	}

	private Entity marked(String mark) {
		Entity victim = mock(Entity.class);
		when(victim.getLocation()).thenReturn(at);
		when(markManager.getMark(victim)).thenReturn(mark);
		return victim;
	}

	private Player playerVictim() {
		Player victim = mock(Player.class);
		when(victim.getLocation()).thenReturn(at);
		return victim;
	}

	@Test
	@DisplayName("countsForWanted delegates through EntityMarks to the NpcMarkManager's persisted mark")
	void countsForWanted_delegatesToMarkManager() {
		Entity victim = mock(Entity.class);
		when(markManager.getMark(victim)).thenReturn("POLICE");

		assertTrue(tracker.countsForWanted(victim));
		verify(markManager).getMark(victim);
	}

	@Test
	@DisplayName("countsForWanted is false for an unmarked entity")
	void countsForWanted_unmarkedEntity_isFalse() {
		Entity victim = mock(Entity.class);
		when(markManager.getMark(victim)).thenReturn(null);

		assertFalse(tracker.countsForWanted(victim));
	}

	@Test
	@DisplayName("onWantedTrigger forwards through KillCombo.setOnWantedLevelTrigger, translating the event to a Player")
	@SuppressWarnings("unchecked")
	void onWantedTrigger_forwardsThroughKillCombo() {
		List<Player> received = new ArrayList<>();
		Player       player   = mock(Player.class);

		tracker.onWantedTrigger(received::add);

		ArgumentCaptor<Consumer<KillComboEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnWantedLevelTrigger(captor.capture());
		captor.getValue().accept(new KillComboEvent(player, mock(KillComboTracker.class)));

		assertEquals(List.of(player), received);
	}

	@Test
	@DisplayName("onComboReset forwards through KillCombo.setOnComboReset, translating the event to a Player")
	@SuppressWarnings("unchecked")
	void onComboReset_forwardsThroughKillCombo() {
		List<Player> received = new ArrayList<>();
		Player       player   = mock(Player.class);

		tracker.onComboReset(received::add);

		ArgumentCaptor<Consumer<KillComboEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnComboReset(captor.capture());
		captor.getValue().accept(new KillComboEvent(player, mock(KillComboTracker.class)));

		assertEquals(List.of(player), received);
	}

	@Test
	@DisplayName("onVictimDeath still reaches KillCombo.setOnPlayerDeath")
	@SuppressWarnings("unchecked")
	void onVictimDeath_stillReachesKillCombo() {
		List<UUID> received = new ArrayList<>();
		UUID       victimId = UUID.randomUUID();

		tracker.onVictimDeath(received::add);

		ArgumentCaptor<Consumer<UUID>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnPlayerDeath(captor.capture());
		captor.getValue().accept(victimId);

		assertEquals(List.of(victimId), received);
	}

	@Test
	@DisplayName("onWantedTrigger reaches the ledger and the combo")
	@SuppressWarnings("unchecked")
	void onWantedTrigger_reachesTheLedgerAndTheCombo() {
		Consumer<Player> handler = p -> {
		};

		tracker.onWantedTrigger(handler);

		verify(ledger).setStarTrigger(handler);
		verify(killCombo).setOnWantedLevelTrigger(any(Consumer.class));
	}

	@Test
	@DisplayName("heat off and combo disabled: a counted kill triggers a star at once")
	void heatOffComboDisabled_triggersAStarAtOnce() throws ReflectiveOperationException {
		heatOff();
		combo(false);
		List<Player> stars = new ArrayList<>();
		tracker.onWantedTrigger(stars::add);

		tracker.recordKill(killer, wanted, marked("POLICE"), 10);

		assertEquals(List.of(killer), stars);
		verify(killCombo, never()).recordKill(any(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("heat off and combo enabled: the kill goes to the combo")
	void heatOffComboEnabled_goesToTheCombo() {
		heatOff();
		Entity victim = marked("POLICE");

		tracker.recordKill(killer, wanted, victim, 10);

		verify(killCombo).recordKill(killer, wanted, victim, 10);
	}

	@Test
	@DisplayName("heat off: a civilian kill reaches the combo path exactly once")
	void heatOff_civilianKill_reachesTheComboPathOnce() {
		heatOff();
		Entity victim = marked("CIVILIAN");

		tracker.recordKill(killer, wanted, victim, 10);

		verify(killCombo).recordKill(killer, wanted, victim, 10);
		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("heat on: a cop kill commits Kill_Cop")
	void heatOn_copKill_commitsKillCop() {
		tracker.recordKill(killer, wanted, marked("POLICE"), 10);

		verify(crimes).commit(killer, Crimes.KILL_COP, at);
		verify(killCombo, never()).recordKill(any(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("heat on: a player kill commits Kill_Player")
	void heatOn_playerKill_commitsKillPlayer() {
		tracker.recordKill(killer, wanted, playerVictim(), 10);

		verify(crimes).commit(killer, Crimes.KILL_PLAYER, at);
	}

	@Test
	@DisplayName("heat on: a civilian kill commits nothing here (gangland-civilians publishes it)")
	void heatOn_civilianKill_commitsNothing() {
		tracker.recordKill(killer, wanted, marked("CIVILIAN"), 10);

		verifyNoInteractions(crimes);
		verify(killCombo, never()).recordKill(any(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("a kill defending your own contested turf mints nothing, heat on and off (TF-49)")
	void defenderKillInsideOwnContestedTurf_commitsNothing_heatOnAndOff() throws ReflectiveOperationException {
		List<Player> stars = new ArrayList<>();
		tracker.onWantedTrigger(stars::add);
		defending = true;
		tracker.recordKill(killer, wanted, playerVictim(), 10);

		heatOff();
		tracker.recordKill(killer, wanted, playerVictim(), 10);
		combo(false);
		tracker.recordKill(killer, wanted, playerVictim(), 10);

		assertTrue(stars.isEmpty());
		verifyNoInteractions(crimes);
		verify(killCombo, never()).recordKill(any(), any(), any(), anyInt());
	}

	@Test
	@DisplayName("the core asks before the kill bounty: a player kill defending your own turf is exempt (TF-49)")
	void exemptsKill_onlyAPlayerKillDefendingYourOwnTurf() {
		defending = true;
		assertTrue(tracker.exemptsKill(killer, playerVictim()));
		assertFalse(tracker.exemptsKill(killer, marked("POLICE")), "a cop kill there keeps full heat");

		defending = false;
		assertFalse(tracker.exemptsKill(killer, playerVictim()));
	}

	@Test
	@DisplayName("the tracker applies Wanted.Kill_Combo.Enable itself, so the core routes every counted kill to it")
	void appliesComboSwitch_isTrue() {
		assertTrue(tracker.appliesComboSwitch());
	}

	@Test
	@DisplayName("an attacker's kill inside a contested turf still commits Kill_Player")
	void attackerKillInsideAContestedTurf_stillCommitsKillPlayer() {
		defending = false;

		tracker.recordKill(killer, wanted, playerVictim(), 10);

		verify(crimes).commit(killer, Crimes.KILL_PLAYER, at);
	}
}
