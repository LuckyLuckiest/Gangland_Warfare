package org.luckyraven.gangland.civilians.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.SpawnConfigProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
	private CivilianMessages    civilianMessages;
	private LivingEntity        victimEntity;
	private Player              attacker;
	private UUID                attackerId;

	// Settings are process-wide statics: an unset Money_Symbol NPEs every GanglandChatUtil.color call (callsigns,
	// SquadRadio lines), so these tests only passed when another suite had loaded Settings first. Same fix as
	// SquadRadioTest: poke the one field.
	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		registry         = mock(CivilianNpcRegistry.class);
		civilianMessages = mock(CivilianMessages.class);
		when(civilianMessages.lines(any())).thenReturn(List.of()); // silent by default; a test overrides what it needs
		service = new CivilianService(mock(JavaPlugin.class), mock(CiviliansLoader.class), mock(NpcMarkManager.class),
		                              mock(CivilianSettings.class), mock(CivilianNpcFactory.class),
		                              mock(CivilianSpawnManager.class), registry, civilianMessages);
		service.civiliansConfig = shoutsConfig(24.0); // matches the shipped civilians.yml: above Alert_Range (16)

		victimEntity = mock(LivingEntity.class);
		attackerId   = UUID.randomUUID();
		attacker     = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(attackerId);
		when(attacker.getLocation()).thenReturn(new Location(mock(World.class), 10, 64, 10));
		when(attacker.getName()).thenReturn("Suspect"); // SquadRadio's %target% needs a non-null name
	}

	/** {@link CiviliansConfig#DEFAULT_SHOUTS} with just {@code Range} overridden, for the shout-recruit boundary tests. */
	private static CiviliansConfig shoutsConfig(double range) {
		RadioSettings base = CiviliansConfig.DEFAULT_SHOUTS;
		RadioSettings shouts = new RadioSettings(base.enabled(), range, base.targetRange(), base.squadGapMs(),
		                                         base.playerGapMs(), base.ackDelayTicks(), base.responderMax(),
		                                         base.cooldownMs(), base.priority(), base.soundName(), base.volume(),
		                                         base.pitch());
		return new CiviliansConfig(List.of(), List.of(), Map.of(), Map.of(), true, 20, shouts);
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
	@DisplayName("the attacker itself is not recruited as its own ally")
	void hit_attackerIsNotRecruitedAsAlly() {
		CivilianNpc  victim         = civilian("gang", 0.0);
		CivilianNpc  attackerNpc    = civilian("gang", 2.0);
		LivingEntity attackerEntity = mock(LivingEntity.class);
		when(attackerEntity.getUniqueId()).thenReturn(attackerId);
		when(attackerEntity.getLocation()).thenReturn(new Location(mock(World.class), 1, 64, 1));
		when(attackerNpc.getEntity()).thenReturn(attackerEntity);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim, attackerNpc));

		service.alertFaction(victim, attackerEntity, false);

		verify(attackerNpc, never()).setTargetPlayerId(any());
		verify(attackerNpc, never()).addEntityTargetToFront(any());
		verify(attackerNpc, never()).joinSquad(any(), any());
		verify(attackerNpc, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a same-faction civilian's stray hit does not rally the faction against its own member")
	void hit_bySameFactionCivilian_doesNotRallyFaction() {
		CivilianNpc  victim         = civilian("gang", 0.0);
		CivilianNpc  ally           = civilian("gang", 5.0);
		CivilianNpc  attackerNpc    = civilian("gang", 2.0);
		LivingEntity attackerEntity = mock(LivingEntity.class);
		UUID         attackerNpcId  = UUID.randomUUID();
		when(attackerEntity.getUniqueId()).thenReturn(attackerNpcId);
		when(attackerEntity.getLocation()).thenReturn(new Location(mock(World.class), 1, 64, 1));
		when(attackerNpc.getEntity()).thenReturn(attackerEntity);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getNpc(attackerNpcId)).thenReturn(attackerNpc);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim, ally, attackerNpc));

		service.alertFaction(victim, attackerEntity, false);

		verify(ally, never()).setTargetPlayerId(any());
		verify(ally, never()).addEntityTargetToFront(any());
		verify(ally, never()).joinSquad(any(), any());
		verify(ally, never()).transitionTo(CivilianState.COMBAT);
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

	// ── Phase H12: faction shouts, shared squads for non-hit entries ───────────────────────────────────────────────

	@Test
	@DisplayName("alertFaction: the squad gets the type's formation arc and the shout listener")
	void alertFaction_squadGetsTypeArcAndShoutListener() {
		CivilianNpc victim = civilian("gang", 0.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim));

		service.alertFaction(victim, attacker, true);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(victim).joinSquad(squad.capture(), eq(attackerId));
		assertEquals(CivilianAIBehaviorConfig.DEFAULT_TACTICS.formationArc(), squad.getValue().getFormationArc());
		// the listener is wired: extras() resolves this squad's faction through the reverse index
		assertEquals(Map.of("faction", "gang"), service.voice.extras(squad.getValue()));
	}

	@Test
	@DisplayName("squadFor shares the same squad alertFaction created, for the same faction and target")
	void squadFor_sharesOneSquadPerFactionAndTarget() {
		CivilianNpc victim = civilian("gang", 0.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim));
		service.alertFaction(victim, attacker, true);

		ArgumentCaptor<NpcSquad> fromHit = ArgumentCaptor.forClass(NpcSquad.class);
		verify(victim).joinSquad(fromHit.capture(), eq(attackerId));

		CivilianNpc reengaging = civilian("gang", 0.0);
		NpcSquad    fromEntry  = service.squadFor(reengaging, attacker);

		assertSame(fromHit.getValue(), fromEntry);
		verify(reengaging).joinSquad(fromEntry, attackerId);
	}

	@Test
	@DisplayName("a Contact edge queues recruitment; it runs only once drained, never from inside the listener")
	void contactShout_recruitedOnNextTick_notInsideListener() {
		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(caller));
		service.alertFaction(caller, attacker, true); // consumes the registry.getActiveNpcs() call below

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squad.capture(), eq(attackerId));
		verify(registry, times(1)).getActiveNpcs();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);

			service.squadListener.onSignal(squad.getValue(), NpcSquadSignal.CONTACT, caller, attacker.getLocation());

			// recruiting scans getActiveNpcs() again; the listener call above must not have triggered it yet
			verify(registry, times(1)).getActiveNpcs();

			service.drainPendingRecruits();

			verify(registry, times(2)).getActiveNpcs();
		}
	}

	@Test
	@DisplayName("a Contact edge recruits allies beyond Alert_Range, out to Shouts.Range - never inside the listener")
	void contactShout_recruitsAlliesBeyondAlertRange_whenShoutRangeLarger() {
		LivingEntity callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));

		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(callerEntity);

		CivilianNpc ally = civilian("turf_defender", 0.0);
		when(ally.distanceTo(callerEntity)).thenReturn(20.0); // beyond Alert_Range 16

		when(registry.getActiveNpcs()).thenReturn(List.of(caller, ally));

		// The hit itself only reaches Alert_Range: the ally is too far to be pulled in by it.
		service.alertFaction(caller, attacker, true);
		verify(ally, never()).joinSquad(any(), any());

		ArgumentCaptor<NpcSquad> squadCaptor = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squadCaptor.capture(), eq(attackerId));
		NpcSquad squad = squadCaptor.getValue();

		// At Shouts.Range no wider than Alert_Range, the shout reaches nobody extra either.
		service.civiliansConfig = shoutsConfig(16.0);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);

			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			verify(ally, never()).joinSquad(any(), any()); // not recruited synchronously, from inside the listener

			service.drainPendingRecruits();
		}
		verify(ally, never()).joinSquad(any(), any());

		// With Shouts.Range above Alert_Range (the shipped config), the same Contact edge now reaches the ally.
		service.civiliansConfig = shoutsConfig(24.0);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);

			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}

		verify(ally).joinSquad(squad, attackerId);
		verify(ally).transitionTo(CivilianState.COMBAT);
	}

	@Test
	@DisplayName("Contact for a target that is itself a same-faction civilian recruits nobody")
	void contactShout_sameFactionTarget_recruitsNobody() {
		CivilianNpc sameFactionTarget = civilian("turf_defender", 0.0);
		when(registry.getNpc(attackerId)).thenReturn(sameFactionTarget);

		LivingEntity callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(callerEntity);

		CivilianNpc ally = civilian("turf_defender", 0.0);
		when(ally.distanceTo(callerEntity)).thenReturn(5.0);

		when(registry.getActiveNpcs()).thenReturn(List.of(caller, ally));
		service.alertFaction(caller, attacker, true); // the hit path already skips - attacker is same faction

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squad.capture(), eq(attackerId));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);

			service.squadListener.onSignal(squad.getValue(), NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}

		verify(ally, never()).joinSquad(any(), any());
	}

	@Test
	@DisplayName("a Rally line is spoken only when the shout recruited at least one ally")
	void rally_saidOnlyWhenRecruitsAboveZero() {
		World        world        = mock(World.class);
		LivingEntity callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(world, 0, 64, 0));

		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(callerEntity);

		when(civilianMessages.lines("Rally")).thenReturn(List.of("%faction%! %count% on the way!"));
		when(civilianMessages.lines("Format")).thenReturn(List.of("[%faction%] %unit%: %line%"));

		Player listener = mock(Player.class);
		when(listener.getWorld()).thenReturn(world);
		when(listener.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(listener));

		// No allies registered: the shout recruits nobody, so nothing is said.
		when(registry.getActiveNpcs()).thenReturn(List.of(caller));
		service.alertFaction(caller, attacker, true);
		ArgumentCaptor<NpcSquad> squadCaptor = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squadCaptor.capture(), eq(attackerId));
		NpcSquad squad = squadCaptor.getValue();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);
			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}
		verify(listener, never()).sendMessage(any(String.class));

		// Now an ally is in range of the wider Shouts.Range: the next Contact recruits it and reports the count.
		CivilianNpc ally = civilian("turf_defender", 0.0);
		when(ally.distanceTo(callerEntity)).thenReturn(20.0);
		when(registry.getActiveNpcs()).thenReturn(List.of(caller, ally));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);
			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}

		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(listener).sendMessage(message.capture());
		assertTrue(message.getValue().contains("1"));
	}

	@Test
	@DisplayName("pruneEmptySquads drops the squad's reverse-index entry along with the squad itself")
	void emptySquads_andReverseIndexPruned() {
		CivilianNpc victim = civilian("gang", 0.0);
		when(victim.getEntity()).thenReturn(victimEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(victim));
		service.alertFaction(victim, attacker, true);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(victim).joinSquad(squad.capture(), eq(attackerId));
		assertEquals(Map.of("faction", "gang"), service.voice.extras(squad.getValue()));

		squad.getValue().remove(victim); // the last member leaves: the squad is now empty
		service.pruneEmptySquads();

		assertEquals(Map.of(), service.voice.extras(squad.getValue()));
	}

	@Test
	@DisplayName("a civilian spawned through CivilianSpawnManager ends up with the service as its FactionSquads")
	void spawnPath_wiresFactionSquads() {
		CivilianNpcRegistry realRegistry = new CivilianNpcRegistry();
		CivilianService wired = new CivilianService(mock(JavaPlugin.class), mock(CiviliansLoader.class),
		                                            mock(NpcMarkManager.class), mock(CivilianSettings.class),
		                                            mock(CivilianNpcFactory.class), mock(CivilianSpawnManager.class),
		                                            realRegistry, civilianMessages);

		CivilianTypeConfig type    = civilian("gang", 0.0).getTypeConfig();
		CiviliansLoader    loader  = mock(CiviliansLoader.class);
		CivilianNpcFactory factory = mock(CivilianNpcFactory.class);
		when(loader.getLoadedConfig()).thenReturn(new CiviliansConfig(List.of(), List.of(), Map.of("gang_member", type),
		                                                              Map.of(), true, 20,
		                                                              CiviliansConfig.DEFAULT_SHOUTS));
		CivilianNpc spawned = mock(CivilianNpc.class);
		when(spawned.isValid()).thenReturn(true);
		when(spawned.getEntity()).thenReturn(victimEntity);
		when(victimEntity.getUniqueId()).thenReturn(UUID.randomUUID());
		when(factory.createCivilian(any(), eq(type), any(), any())).thenReturn(spawned);

		CivilianSpawnManager spawnManager = new CivilianSpawnManager(mock(SpawnConfigProvider.class),
		                                                             mock(IRepository.class), factory, realRegistry,
		                                                             loader);
		assertSame(spawned, spawnManager.spawnCivilian(new Location(mock(World.class), 0, 64, 0), "gang_member"));

		verify(spawned).setFactionSquads(wired);
	}

	@Test
	@DisplayName("a second Contact recruits nobody new when every ally in range is already in the squad: no Rally")
	void secondContact_alreadyInSquad_notCountedAgain() {
		World        world        = mock(World.class);
		LivingEntity callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(callerEntity);

		when(civilianMessages.lines("Rally")).thenReturn(List.of("%count% on the way!"));
		when(civilianMessages.lines("Format")).thenReturn(List.of("%line%"));
		Player listener = mock(Player.class);
		when(listener.getWorld()).thenReturn(world);
		when(listener.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(listener));

		when(registry.getActiveNpcs()).thenReturn(List.of(caller));
		service.alertFaction(caller, attacker, true);
		ArgumentCaptor<NpcSquad> squadCaptor = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squadCaptor.capture(), eq(attackerId));
		NpcSquad squad = squadCaptor.getValue();

		// the ally was recruited earlier and is still in this squad, fighting the same target
		CivilianNpc ally = civilian("turf_defender", 0.0);
		when(ally.distanceTo(callerEntity)).thenReturn(5.0);
		when(ally.getCurrentState()).thenReturn(CivilianState.COMBAT);
		when(ally.getTargetPlayerId()).thenReturn(attackerId);
		when(ally.getSquad()).thenReturn(squad);
		squad.add(ally);
		when(registry.getActiveNpcs()).thenReturn(List.of(caller, ally));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class)); // no Citizens: no NPCs
			when(attacker.isValid()).thenReturn(true);
			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}

		verify(listener, never()).sendMessage(any(String.class));
	}

	@Test
	@DisplayName("a Contact against a Player-typed NPC (a cop) queues it as an entity target, not a player id")
	void contactShout_playerTypedNpcTarget_queuedAsEntity() {
		Player cop = mock(Player.class);
		when(cop.getUniqueId()).thenReturn(attackerId);
		when(cop.getLocation()).thenReturn(new Location(mock(World.class), 10, 64, 10));
		when(cop.getName()).thenReturn("Officer");
		when(cop.isValid()).thenReturn(true);

		LivingEntity callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
		CivilianNpc caller = civilian("turf_defender", 0.0);
		when(caller.getEntity()).thenReturn(callerEntity);
		CivilianNpc ally = civilian("turf_defender", 0.0);
		when(ally.distanceTo(callerEntity)).thenReturn(20.0); // only the shout reaches it

		when(registry.getActiveNpcs()).thenReturn(List.of(caller, ally));
		service.alertFaction(caller, cop, false);
		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller).joinSquad(squad.capture(), eq(attackerId));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
		     MockedStatic<NpcSupport> npcs = mockStatic(NpcSupport.class)) {
			bukkit.when(() -> Bukkit.getEntity(attackerId)).thenReturn(cop); // Bukkit.getPlayer misses a Citizens NPC
			npcs.when(() -> NpcSupport.isNpc(cop)).thenReturn(true);
			service.squadListener.onSignal(squad.getValue(), NpcSquadSignal.CONTACT, caller, cop.getLocation());
			service.drainPendingRecruits();
		}

		verify(ally).addEntityTargetToFront(cop);
		verify(ally, never()).setTargetPlayerId(any());
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
