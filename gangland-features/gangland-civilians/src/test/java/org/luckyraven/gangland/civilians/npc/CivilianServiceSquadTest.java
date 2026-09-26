package org.luckyraven.gangland.civilians.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.10): a hit on a hostile civilian puts the victim in its faction's squad against the attacker
 * (keyed by the attacker's uuid - player or entity) and pulls in every same-faction, combat-enabled civilian within
 * its own alert range that is not busy with another target. One hop; the squad lives until its target's squads are
 * dropped (or it empties).
 */
@DisplayName("CivilianService - faction squads")
class CivilianServiceSquadTest {

	private CivilianNpcRegistry registry;
	private CivilianService     service;
	private LivingEntity        victimEntity;
	private Player              attacker;
	private UUID                attackerId;

	@BeforeEach
	void setUp() {
		registry = mock(CivilianNpcRegistry.class);
		service  = new CivilianService(mock(JavaPlugin.class), mock(CiviliansLoader.class), mock(NpcMarkManager.class),
		                               mock(CivilianSettings.class), mock(CivilianNpcFactory.class),
		                               mock(CivilianSpawnManager.class), registry);

		victimEntity = mock(LivingEntity.class);
		attackerId   = UUID.randomUUID();
		attacker     = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(attackerId);
		when(attacker.getLocation()).thenReturn(new Location(mock(World.class), 10, 64, 10));
	}

	@Test
	@DisplayName("same faction in range joins; a far one and another faction do not")
	void hit_alertsSameFactionInRangeOnly() {
		CivilianNpc victim   = civilian("gang", 0.0);
		CivilianNpc ally     = civilian("gang", 10.0);
		CivilianNpc far      = civilian("gang", 30.0);
		CivilianNpc stranger = civilian("cartel", 5.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim, ally, far, stranger));

		service.alertFaction(victim, attacker, true);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(victim).joinSquad(squad.capture(), eq(attackerId));
		assertTrue(squad.getValue().hasFreshSighting(), "the hit reports where the attacker is");

		verify(ally).setTargetPlayerId(attackerId);
		verify(ally).joinSquad(squad.getValue(), attackerId);
		verify(ally).transitionTo(CivilianState.COMBAT);

		verify(far, never()).joinSquad(any(), any());
		verify(far, never()).transitionTo(any());
		verify(stranger, never()).joinSquad(any(), any());
		verify(stranger, never()).transitionTo(any());
	}

	@Test
	@DisplayName("an ally already fighting another target is not pulled off it")
	void hit_skipsAllyBusyWithAnotherTarget() {
		CivilianNpc victim = civilian("gang", 0.0);
		CivilianNpc busy   = civilian("gang", 5.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(busy.getCurrentState()).thenReturn(CivilianState.COMBAT);
		when(busy.getTargetPlayerId()).thenReturn(UUID.randomUUID());
		when(registry.getActiveNpcs()).thenReturn(List.of(victim, busy));

		service.alertFaction(victim, attacker, true);

		verify(busy, never()).setTargetPlayerId(any());
		verify(busy, never()).joinSquad(any(), any());
	}

	@Test
	@DisplayName("an NPC attacker goes to the front of the allies' entity target queue")
	void entityAttacker_isQueuedForAllies() {
		CivilianNpc  victim = civilian("gang", 0.0);
		CivilianNpc  ally   = civilian("gang", 10.0);
		LivingEntity cop    = mock(LivingEntity.class);
		when(cop.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim, ally));

		service.alertFaction(victim, cop, false);

		verify(ally).addEntityTargetToFront(cop);
		verify(ally, never()).setTargetPlayerId(any());
		verify(ally).transitionTo(CivilianState.COMBAT);
	}

	@Test
	@DisplayName("one squad per faction and target, until the target's squads are dropped")
	void squads_sharedUntilDropped() {
		CivilianNpc victim = civilian("gang", 0.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim));

		service.alertFaction(victim, attacker, true);
		service.alertFaction(victim, attacker, true);
		service.dropSquads(attackerId);
		service.alertFaction(victim, attacker, true);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(victim, times(3)).joinSquad(squad.capture(), eq(attackerId));
		List<NpcSquad> squads = squad.getAllValues();
		assertSame(squads.get(0), squads.get(1));
		assertNotSame(squads.get(1), squads.get(2));
	}

	private CivilianNpc civilian(String faction, double distanceToVictim) {
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, 16.0, 20);
		CivilianTypeConfig type = new CivilianTypeConfig("gang_member", "Gang Member", EntityType.PLAYER, 25.0, true,
		                                                 new CivilianWearableConfig("", "", "", ""), List.of(),
		                                                 List.of(), List.of(), new CivilianDropConfig(List.of(), 0.0),
		                                                 ai, faction);
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.getTypeConfig()).thenReturn(type);
		when(npc.isValid()).thenReturn(true);
		when(npc.isHostile()).thenReturn(true);
		when(npc.getCurrentState()).thenReturn(CivilianState.IDLE);
		when(npc.distanceTo(victimEntity)).thenReturn(distanceToVictim);
		return npc;
	}
}
