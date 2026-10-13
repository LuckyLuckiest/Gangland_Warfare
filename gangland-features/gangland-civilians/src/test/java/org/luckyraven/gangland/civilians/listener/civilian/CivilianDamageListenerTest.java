package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CivilianDamageListener - hits on civilians feed the witness service (P-WIT-3, P-WIT-6)")
class CivilianDamageListenerTest {

	private WitnessTestKit           kit;
	private CivilianDamageListener   listener;
	private MockedStatic<NpcSupport> npcSupport;

	@BeforeEach
	void setUp() {
		kit        = new WitnessTestKit();
		listener   = new CivilianDamageListener(kit.civilians, kit.service);
		npcSupport = mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
	}

	@Test
	@DisplayName("two hits inside 10 s publish one Assault_Civilian; a third at 11 s publishes the second")
	void assaultCivilianThrottled() {
		CivilianNpc victim = registered(kit.civilian(6, false), false, true);

		listener.onCivilianDamage(hit(kit.criminal, victim));
		kit.advanceSeconds(4);
		listener.onCivilianDamage(hit(kit.criminal, victim));
		verify(kit.crimes, times(1)).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));

		kit.advanceSeconds(7);
		listener.onCivilianDamage(hit(kit.criminal, victim));
		verify(kit.crimes, times(2)).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));
	}

	@Test
	@DisplayName("a non-hostile civilian still flees from the hit (shipped behaviour unchanged)")
	void nonHostileStillFlees() {
		CivilianNpc victim = registered(kit.civilian(6, false), false, true);

		listener.onCivilianDamage(hit(kit.criminal, victim));

		assertEquals(CivilianState.FLEEING, victim.getCurrentState());
	}

	@Test
	@DisplayName("a hostile civilian still fights back and the hit is still an Assault_Civilian")
	void hostileStillFightsAndAssaultPublished() {
		CivilianNpc victim = registered(kit.civilian(6, false), true, true);

		listener.onCivilianDamage(hit(kit.criminal, victim));

		assertEquals(CivilianState.COMBAT, victim.getCurrentState());
		verify(kit.crimes).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));
	}

	@Test
	@DisplayName("hitting the caller cancels the call: it flees and nothing lands 8 s later")
	void hitOnCallerCancelsCall() {
		CivilianNpc caller = registered(kit.civilian(10, true), false, true);
		kit.witness("Car_Theft");

		kit.advanceSeconds(4);
		listener.onCivilianDamage(hit(kit.criminal, caller));
		kit.advanceSeconds(4);
		kit.service.tickCaller(caller);

		assertEquals(CivilianState.FLEEING, caller.getCurrentState());
		assertNull(caller.getWitnessCall());
		assertEquals(0, kit.countEvents(CivilianCalledInEvent.class));
	}

	@Test
	@DisplayName("an arrow shot by a player counts as that player hitting the civilian")
	void projectileFromPlayerPublishes() {
		CivilianNpc victim = registered(kit.civilian(6, false), false, true);
		Arrow       arrow  = mock(Arrow.class);
		when(arrow.getShooter()).thenReturn(kit.criminal);

		listener.onCivilianDamage(event(arrow, victim));

		verify(kit.crimes).commit(eq(kit.criminal), eq(Crimes.ASSAULT_CIVILIAN), any(Location.class));
	}

	@Test
	@DisplayName("a Citizens NPC player hitting a civilian is no crime")
	void npcAttackerPublishesNothing() {
		CivilianNpc victim = registered(kit.civilian(6, false), false, true);
		Player      npcCop = mock(Player.class);
		when(npcCop.getLocation()).thenReturn(new Location(null, 1, 64, 1));
		npcSupport.when(() -> NpcSupport.isNpc(npcCop)).thenReturn(true);

		listener.onCivilianDamage(event(npcCop, victim));

		verify(kit.crimes, never()).commit(any(), any(), any());
	}

	@Test
	@DisplayName("a mob hitting a civilian is no crime")
	void mobAttackerPublishesNothing() {
		CivilianNpc victim = registered(kit.civilian(6, false), false, true);

		Zombie zombie = mock(Zombie.class);
		when(zombie.getLocation()).thenReturn(new Location(null, 1, 64, 1));

		listener.onCivilianDamage(event(zombie, victim));

		verify(kit.crimes, never()).commit(any(), any(), any());
	}

	@Test
	@DisplayName("the listener hands the player and the victim to WitnessService.onPlayerHitCivilian")
	void delegatesToService() {
		WitnessService service  = mock(WitnessService.class);
		CivilianNpc    victim   = registered(kit.civilian(6, false), false, true);
		new CivilianDamageListener(kit.civilians, service).onCivilianDamage(hit(kit.criminal, victim));

		verify(service).onPlayerHitCivilian(kit.criminal, victim);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private CivilianNpc registered(CivilianNpc npc, boolean hostile, boolean fleeEnabled) {
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, fleeEnabled, 16, hostile, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, 16.0, 20);
		when(npc.getTypeConfig()).thenReturn(new CivilianTypeConfig("t", "T", EntityType.PLAYER, 20.0, hostile,
		                                                            new CivilianWearableConfig("", "", "", ""),
		                                                            List.of(), List.of(), List.of(),
		                                                            new CivilianDropConfig(List.of(), 0.0), ai, "t"));
		when(npc.isHostile()).thenReturn(hostile);
		when(kit.civilians.getNpc(npc.getEntity().getUniqueId())).thenReturn(npc);
		return npc;
	}

	private EntityDamageByEntityEvent hit(Player attacker, CivilianNpc victim) {
		return event(attacker, victim);
	}

	@SuppressWarnings("deprecation")
	private EntityDamageByEntityEvent event(Entity damager, CivilianNpc victim) {
		LivingEntity entity = victim.getEntity();
		return new EntityDamageByEntityEvent(damager, entity, DamageCause.ENTITY_ATTACK, 5.0);
	}
}
