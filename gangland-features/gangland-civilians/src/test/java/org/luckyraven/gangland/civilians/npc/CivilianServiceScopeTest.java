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
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TF-47: a custom {@link FactionSquads} provider (turf guards) gets its own scoped squads, and recruitment never
 * crosses providers - two turfs' guards, or a turf guard and a street civilian of the same faction.
 */
@DisplayName("CivilianService - scoped faction squads")
class CivilianServiceScopeTest {

	private CivilianNpcRegistry registry;
	private CivilianService     service;
	private CivilianMessages    messages;
	private World               world;
	private LivingEntity        callerEntity;
	private Player              attacker;
	private UUID                attackerId;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		registry = mock(CivilianNpcRegistry.class);
		messages = mock(CivilianMessages.class);
		when(messages.lines(any())).thenReturn(List.of());
		service = new CivilianService(mock(JavaPlugin.class), mock(CiviliansLoader.class), mock(NpcMarkManager.class),
		                              mock(CivilianSettings.class), mock(CivilianNpcFactory.class),
		                              mock(CivilianSpawnManager.class), registry, messages);
		service.civiliansConfig = new CiviliansConfig(List.of(), List.of(), Map.of(), Map.of(), true, 20,
		                                              CiviliansConfig.DEFAULT_SHOUTS);

		world        = mock(World.class);
		callerEntity = mock(LivingEntity.class);
		when(callerEntity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		attackerId = UUID.randomUUID();
		attacker   = playerWithId(attackerId);
	}

	private Player playerWithId(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));
		when(player.getName()).thenReturn("Suspect");
		when(player.isValid()).thenReturn(true);
		return player;
	}

	/** A turf-style provider: its own scope, squads made by the service, optional gang extras. */
	private final class Provider implements FactionSquads {

		private final String scope;
		private final String gang;

		Provider(String scope, String gang) {
			this.scope = scope;
			this.gang  = gang;
		}

		@Override
		public NpcSquad squadFor(CivilianNpc npc, LivingEntity target) {
			return service.squadFor(npc, target, scope);
		}

		@Override
		public String scope() {
			return scope;
		}

		@Override
		public Map<String, String> extras(NpcSquad squad) {
			return gang == null ? Map.of() : Map.of("gang", gang);
		}
	}

	@Test
	@DisplayName("same faction in two scopes never shares a squad; scope '' is the unscoped squad")
	void sameFactionDifferentScope_neverShareASquad() {
		CivilianNpc a = civilian("turf_defender", 0.0, null);
		CivilianNpc b = civilian("turf_defender", 0.0, null);
		CivilianNpc c = civilian("turf_defender", 0.0, null);

		NpcSquad one   = service.squadFor(a, attacker, "turf:1");
		NpcSquad two   = service.squadFor(b, attacker, "turf:2");
		NpcSquad plain = service.squadFor(c, attacker, "");

		assertNotSame(one, two);
		assertNotSame(one, plain);
		assertSame(plain, service.squadFor(c, attacker));
		assertSame(one, service.squadFor(a, attacker, "turf:1"));
	}

	@Test
	@DisplayName("a custom provider's alert and shout recruit only its own NPCs")
	void customProvider_recruitsOnlyItsOwnNpcs() {
		Provider p1 = new Provider("turf:1", null);
		Provider p2 = new Provider("turf:2", null);

		CivilianNpc caller = civilian("turf_defender", 0.0, p1);
		CivilianNpc mate   = civilian("turf_defender", 10.0, p1);
		CivilianNpc far    = civilian("turf_defender", 20.0, p1); // beyond Alert_Range, inside Shouts.Range
		CivilianNpc other  = civilian("turf_defender", 5.0, p2);
		CivilianNpc street = civilian("turf_defender", 5.0, null);
		when(caller.getEntity()).thenReturn(callerEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(caller, mate, far, other, street));

		service.alertFaction(caller, attacker, true);

		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(caller, atLeastOnce()).joinSquad(squad.capture(), eq(attackerId));
		verify(mate).joinSquad(any(), eq(attackerId));
		verify(other, never()).joinSquad(any(), any());
		verify(street, never()).joinSquad(any(), any());
		verify(far, never()).joinSquad(any(), any());

		service.civiliansConfig = new CiviliansConfig(List.of(), List.of(), Map.of(), Map.of(), true, 20,
		                                              CiviliansConfig.DEFAULT_SHOUTS);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
			service.squadListener.onSignal(squad.getValue(), NpcSquadSignal.CONTACT, caller, attacker.getLocation());
			service.drainPendingRecruits();
		}

		verify(far).joinSquad(any(), eq(attackerId)); // shout range reaches the same-provider guard
		verify(other, never()).joinSquad(any(), any());
		verify(street, never()).joinSquad(any(), any());
	}

	@Test
	@DisplayName("a street civilian neither joins a custom squad nor recruits custom NPCs")
	void streetCivilian_neverJoinsACustomSquad() {
		Provider p1 = new Provider("turf:1", null);

		CivilianNpc guard      = civilian("turf_defender", 0.0, p1);
		CivilianNpc street     = civilian("turf_defender", 5.0, null);
		CivilianNpc streetMate = civilian("turf_defender", 5.0, null);
		when(guard.getEntity()).thenReturn(callerEntity);
		when(street.getEntity()).thenReturn(callerEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(guard, street, streetMate));

		service.alertFaction(guard, attacker, true);
		verify(street, never()).joinSquad(any(), any());
		verify(streetMate, never()).joinSquad(any(), any());

		clearInvocations(guard);
		service.alertFaction(street, attacker, true); // default hit: recruits the street mate, never the guard
		verify(streetMate).joinSquad(any(), eq(attackerId));
		verify(guard, never()).joinSquad(any(), any());
	}

	@Test
	@DisplayName("two attackers on one scope give one squad each, stable while the guards alternate")
	void twoAttackersOneTurf_oneSquadPerTarget() {
		Provider p1       = new Provider("turf:1", null);
		UUID     secondId = UUID.randomUUID();
		Player   second   = playerWithId(secondId);

		CivilianNpc guardA = civilian("turf_defender", 0.0, p1);
		CivilianNpc guardB = civilian("turf_defender", 0.0, p1);
		when(guardA.getEntity()).thenReturn(callerEntity);
		when(guardB.getEntity()).thenReturn(callerEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(guardA, guardB));

		service.alertFaction(guardA, attacker, true);
		service.alertFaction(guardB, second, true);
		service.alertFaction(guardA, attacker, true);
		service.alertFaction(guardB, second, true);

		ArgumentCaptor<NpcSquad> a = ArgumentCaptor.forClass(NpcSquad.class);
		ArgumentCaptor<NpcSquad> b = ArgumentCaptor.forClass(NpcSquad.class);
		verify(guardA, atLeastOnce()).joinSquad(a.capture(), eq(attackerId));
		verify(guardB, atLeastOnce()).joinSquad(b.capture(), eq(secondId));
		assertNotSame(a.getValue(), b.getValue());
		assertEquals(1, a.getAllValues().stream().distinct().count());
		assertEquals(1, b.getAllValues().stream().distinct().count());
	}

	@Test
	@DisplayName("provider extras reach the relayed shout and shout(...); the default adds nothing and leaves no %gang%")
	void providerExtras_reachTheShout() {
		when(messages.lines("Format")).thenReturn(List.of("[%faction%/%gang%] %unit%: %line%"));
		when(messages.lines("Contact")).thenReturn(List.of("Over here!"));
		when(messages.lines("Turf_Held")).thenReturn(List.of("Held for %gang%"));
		Player listener = mock(Player.class);
		when(listener.getWorld()).thenReturn(world);
		when(listener.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(listener));

		Provider    vip    = new Provider("turf:1", "VIP");
		CivilianNpc guard  = civilian("turf_defender", 0.0, vip);
		CivilianNpc street = civilian("turf_defender", 0.0, null);
		when(guard.getEntity()).thenReturn(callerEntity);
		when(street.getEntity()).thenReturn(callerEntity);
		when(registry.getActiveNpcs()).thenReturn(List.of(guard));

		service.alertFaction(guard, attacker, true);
		ArgumentCaptor<NpcSquad> squad = ArgumentCaptor.forClass(NpcSquad.class);
		verify(guard, atLeastOnce()).joinSquad(squad.capture(), eq(attackerId));
		fire(squad.getValue(), guard);

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(listener).sendMessage(text.capture());
		assertTrue(text.getValue().contains("VIP"), text.getValue());

		// a fresh listener: the first one is inside its player gap after the relayed Contact
		Player fresh = mock(Player.class);
		when(fresh.getUniqueId()).thenReturn(UUID.randomUUID());
		when(fresh.getWorld()).thenReturn(world);
		when(fresh.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(fresh));
		service.shout(guard, "Turf_Held", Map.of());
		verify(fresh).sendMessage(text.capture());
		assertTrue(text.getValue().contains("Held for VIP"), text.getValue());

		// default provider: nothing extra, and an unresolved %gang% never leaks into the chat line
		Player last = mock(Player.class);
		when(last.getUniqueId()).thenReturn(UUID.randomUUID());
		when(last.getWorld()).thenReturn(world);
		when(last.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(last));
		NpcSquad plain = service.squadFor(street, attacker);
		assertEquals(Map.of("faction", "turf_defender"), service.voice.extras(plain));
		fire(plain, street);
		verify(last).sendMessage(text.capture());
		assertFalse(text.getValue().contains("%gang%"), text.getValue());
	}

	private void fire(NpcSquad squad, CivilianNpc caller) {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(attackerId)).thenReturn(attacker);
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
			service.squadListener.onSignal(squad, NpcSquadSignal.CONTACT, caller, attacker.getLocation());
		}
	}

	private CivilianNpc civilian(String faction, double distanceToCaller, FactionSquads provider) {
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
		when(npc.distanceTo(callerEntity)).thenReturn(distanceToCaller);
		when(npc.getFactionSquads()).thenReturn(provider);
		return npc;
	}
}
