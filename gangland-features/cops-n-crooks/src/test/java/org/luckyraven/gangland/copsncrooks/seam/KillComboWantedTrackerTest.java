package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.combo.KillComboTracker;
import org.luckyraven.gangland.copsncrooks.events.combo.KillComboEvent;
import org.luckyraven.gangland.copsncrooks.heat.Crime;
import org.luckyraven.gangland.copsncrooks.heat.HeatService;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the seam-3 delegate: {@code onWantedTrigger}/{@code onComboReset}/{@code onVictimDeath} must forward through
 * to {@link KillCombo}'s three setters (translating the {@link KillComboEvent} down to the {@link Player} the core
 * {@code WantedKillTracker} contract expects), and {@code countsForWanted} must delegate through
 * {@code EntityMarks.countsForWanted} to the shared {@link NpcMarkManager}'s persisted mark. See
 * documentation/module-loader.md, "Core seams".
 */
@DisplayName("KillComboWantedTracker")
class KillComboWantedTrackerTest {

	private KillCombo              killCombo;
	private NpcMarkManager         markManager;
	private KillComboWantedTracker tracker;

	@BeforeEach
	void setUp() {
		killCombo   = mock(KillCombo.class);
		markManager = mock(NpcMarkManager.class);
		tracker     = new KillComboWantedTracker(killCombo, markManager);
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
	@DisplayName("onVictimDeath forwards through KillCombo.setOnPlayerDeath")
	@SuppressWarnings("unchecked")
	void onVictimDeath_forwardsThroughKillCombo() {
		List<UUID> received = new ArrayList<>();
		UUID       victimId = UUID.randomUUID();

		tracker.onVictimDeath(received::add);

		ArgumentCaptor<Consumer<UUID>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(killCombo).setOnPlayerDeath(captor.capture());
		captor.getValue().accept(victimId);

		assertEquals(List.of(victimId), received);
	}

	// ---- 0.12 heat ledger + self-defence exemption ----

	@Test
	@DisplayName("killing a hostile civilian in COMBAT is self-defence and does not count for wanted")
	void countsForWanted_hostileCivilianInCombat_isSelfDefence() {
		CivilianNpcRegistry    registry = mock(CivilianNpcRegistry.class);
		KillComboWantedTracker heat     = new KillComboWantedTracker(killCombo, markManager, registry, null);

		Entity victim   = mock(Entity.class);
		UUID   victimId = UUID.randomUUID();
		when(victim.getUniqueId()).thenReturn(victimId);
		when(markManager.getMark(victim)).thenReturn("CIVILIAN");

		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.isHostile()).thenReturn(true);
		when(npc.getCurrentState()).thenReturn(CivilianState.COMBAT);
		when(registry.getNpc(victimId)).thenReturn(npc);

		assertFalse(heat.countsForWanted(victim));

		when(npc.getCurrentState()).thenReturn(CivilianState.WANDERING);
		assertTrue(heat.countsForWanted(victim), "a hostile civilian that is not fighting back still counts");

		when(npc.isHostile()).thenReturn(false);
		when(npc.getCurrentState()).thenReturn(CivilianState.COMBAT);
		assertTrue(heat.countsForWanted(victim), "a peaceful civilian always counts");
	}

	@Test
	@DisplayName("scoresAllKills follows the heat service's Enable flag and is false without one")
	void scoresAllKills_followsHeatService() {
		assertFalse(tracker.scoresAllKills());

		HeatService            heatService = mock(HeatService.class);
		KillComboWantedTracker heat        = new KillComboWantedTracker(killCombo, markManager, null, heatService);

		when(heatService.isEnabled()).thenReturn(true);
		assertTrue(heat.scoresAllKills());

		when(heatService.isEnabled()).thenReturn(false);
		assertFalse(heat.scoresAllKills());
	}

	@Test
	@DisplayName("with heat on, a kill is scored in heat and fed to the combo without the legacy thresholds")
	void recordKill_heatOn_routesThroughHeat() {
		HeatService            heatService = mock(HeatService.class);
		KillComboWantedTracker heat        = new KillComboWantedTracker(killCombo, markManager, null, heatService);

		Player   killer   = mock(Player.class);
		Wanted   wanted   = mock(Wanted.class);
		Entity   victim   = mock(Entity.class);
		Location location = mock(Location.class);
		when(victim.getLocation()).thenReturn(location);
		when(heatService.isEnabled()).thenReturn(true);
		when(heatService.classifyKill(victim)).thenReturn(Crime.KILL_COP);
		when(heatService.recordCrime(killer, wanted, Crime.KILL_COP, location)).thenReturn(150);

		heat.recordKill(killer, wanted, victim, 30);

		verify(heatService).recordCrime(killer, wanted, Crime.KILL_COP, location);
		verify(killCombo).recordKill(killer, wanted, victim, 30, 150, false);
		verify(killCombo, never()).recordKill(killer, wanted, victim, 30);
	}

	@Test
	@DisplayName("with heat off, a kill goes through the legacy one-point combo path")
	void recordKill_heatOff_usesLegacyPath() {
		HeatService            heatService = mock(HeatService.class);
		KillComboWantedTracker heat        = new KillComboWantedTracker(killCombo, markManager, null, heatService);
		when(heatService.isEnabled()).thenReturn(false);

		Player killer = mock(Player.class);
		Wanted wanted = mock(Wanted.class);
		Entity victim = mock(Entity.class);

		heat.recordKill(killer, wanted, victim, 30);

		verify(killCombo).recordKill(killer, wanted, victim, 30);
		verify(killCombo, never()).recordKill(any(), any(), any(), anyInt(), anyInt(), anyBoolean());
		verify(heatService, never()).recordCrime(any(), any(), any(), any());
	}

	@Test
	@DisplayName("onHeatStarTrigger installs the handler on the heat service")
	@SuppressWarnings("unchecked")
	void onHeatStarTrigger_installsOnHeatService() {
		HeatService            heatService = mock(HeatService.class);
		KillComboWantedTracker heat        = new KillComboWantedTracker(killCombo, markManager, null, heatService);

		BiConsumer<Player, Integer> handler = mock(BiConsumer.class);
		heat.onHeatStarTrigger(handler);

		verify(heatService).setStarTrigger(handler);
	}
}
