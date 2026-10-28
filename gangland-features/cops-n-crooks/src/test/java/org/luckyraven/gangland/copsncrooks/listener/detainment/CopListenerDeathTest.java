package org.luckyraven.gangland.copsncrooks.listener.detainment;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.npc.spi.NpcSquadListener;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-111: a cop's entity is no longer valid during its own {@link EntityDeathEvent}, so the old
 * {@code isCopNpc} gate (which requires a valid entity) skipped every cop death. The dying cop is found without the
 * validity check, reported down to its squads, then marked for removal: destroying it inside the event would
 * despawn it with reason PLUGIN and delete the body before its death animation.
 */
@DisplayName("CopListener - cop deaths")
class CopListenerDeathTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void installBukkit() {
		bukkit = BukkitStatics.install(); // onCopDeath ends by firing CopDeathEvent through Bukkit
	}

	@AfterEach
	void closeBukkit() {
		bukkit.close();
	}

	@Test
	@DisplayName("a dying (already invalid) cop is found, reported down to its squad, then marked for removal (body kept)")
	void onCopDeath_findsDyingCopWhileInvalid_callsMemberDownThenMarksForRemoval() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.isCopNpc(body)).thenReturn(false); // invalid during the death event
		when(manager.findDyingCop(body)).thenReturn(cop);

		CopGroup         group    = new CopGroup(UUID.randomUUID());
		NpcSquadListener listener = mock(NpcSquadListener.class);
		group.setListener(listener);
		group.add(cop);
		group.add(mock(CopNpc.class)); // someone left to lead
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(group.getSquad());

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		InOrder order = inOrder(listener, cop);
		order.verify(listener).onSignal(eq(group.getSquad()), any(NpcSquadSignal.class), eq(cop), any());
		order.verify(cop).markForRemoval();
		verify(cop, never()).destroy(); // destroy() despawns with PLUGIN and deletes the body mid-death
		assertFalse(group.getSquad().members().contains(cop));
	}

	@Test
	@DisplayName("a cop killed while cuffing (no current squad, still in the group squad) is still reported down")
	void onCopDeath_midCuff_noCurrentSquad_groupSquadStillReportsDown() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.findDyingCop(body)).thenReturn(cop);

		CopGroup         group    = new CopGroup(UUID.randomUUID());
		NpcSquadListener listener = mock(NpcSquadListener.class);
		group.setListener(listener);
		group.add(cop);
		group.add(mock(CopNpc.class));
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(null); // CUFFING: stopNavigation cleared the pursuit's squad

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		verify(listener).onSignal(eq(group.getSquad()), any(NpcSquadSignal.class), eq(cop), any());
		assertFalse(group.getSquad().members().contains(cop));
		verify(cop).markForRemoval();
		verify(cop, never()).destroy();
	}

	@Test
	@DisplayName("a cop killed fighting an attacker is reported down once, to the attacker squad only")
	void onCopDeath_inAttackerSquad_notInGroupSquad_reportedOnceToAttackerSquad() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.findDyingCop(body)).thenReturn(cop);

		CopGroup         group    = new CopGroup(UUID.randomUUID());
		NpcSquadListener listener = mock(NpcSquadListener.class);
		group.setListener(listener);
		group.add(cop);
		group.add(mock(CopNpc.class));
		group.getSquad().remove(cop); // squadFor moved it to the attacker squad
		NpcSquad attackers = group.attackerSquad(UUID.randomUUID(), null);
		attackers.add(cop);
		attackers.add(mock(CopNpc.class));
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(attackers);

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		verify(listener).onSignal(eq(attackers), any(NpcSquadSignal.class), eq(cop), any());
		verify(listener, never()).onSignal(eq(group.getSquad()), any(), any(), any());
		verify(cop).markForRemoval();
		verify(cop, never()).destroy();
	}

	@Test
	@DisplayName("the death is handled before Citizens (LOW) despawns the NPC and detaches its entity")
	void onCopDeath_runsBeforeCitizensDespawn() throws NoSuchMethodException {
		EventHandler handler = CopListener.class.getMethod("onCopDeath", EntityDeathEvent.class)
		                                        .getAnnotation(EventHandler.class);
		assertEquals(EventPriority.LOWEST, handler.priority());
	}

	@Test
	@DisplayName("T-121: a cop killed walking home (in no squad) is still reported down to its group squad")
	void onCopDeath_returningCopInNoSquad_reportedDownToGroupSquad() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.findDyingCop(body)).thenReturn(cop);

		CopGroup         group    = new CopGroup(UUID.randomUUID());
		NpcSquadListener listener = mock(NpcSquadListener.class);
		group.setListener(listener);
		group.add(cop);
		group.add(mock(CopNpc.class));
		group.getSquad().remove(cop); // RETURNING: leaveSquad() dropped it from every squad
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(null);

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		verify(listener).onSignal(eq(group.getSquad()), eq(NpcSquadSignal.MAN_DOWN), eq(cop), any());
		assertFalse(group.getSquad().members().contains(cop));
		verify(cop).markForRemoval();
		verify(cop, never()).destroy();
	}

	@Test
	@DisplayName("a death that is not a cop's is left alone")
	void nonCopDeath_untouched() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		EntityDeathEvent event = deathOf(body);
		event.getDrops().add(mock(ItemStack.class));

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(event);

		verify(manager, never()).findCopByEntity(any());
		assertFalse(event.getDrops().isEmpty());
	}

	@Test
	@DisplayName("a cop death fires CopDeathEvent through Bukkit with the killer (CJ-39)")
	void copDeath_firesCopDeathEvent_withTheKiller() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		Player       killer  = mock(Player.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.findDyingCop(body)).thenReturn(cop);
		when(body.getKiller()).thenReturn(killer);

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		ArgumentCaptor<CopDeathEvent> captor = ArgumentCaptor.forClass(CopDeathEvent.class);
		verify(bukkit.pluginManager()).callEvent(captor.capture());
		assertSame(killer, captor.getValue().getKiller());
	}

	@Test
	@DisplayName("a cop that died to the world (no killer) still fires CopDeathEvent, with a null killer")
	void copDeath_noKiller_firesWithNull() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		when(manager.findDyingCop(body)).thenReturn(cop);

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		ArgumentCaptor<CopDeathEvent> captor = ArgumentCaptor.forClass(CopDeathEvent.class);
		verify(bukkit.pluginManager()).callEvent(captor.capture());
		assertNull(captor.getValue().getKiller());
	}

	/**
	 * Final fix round 1: the cold-trail hold is only re-evaluated on the next AI tick, but the officer-down backup is
	 * requested synchronously inside this event. Killing a cop is a crime, so the suspect's kill releases the hold
	 * before the squad hears of it; anyone else's kill leaves it alone.
	 */
	@Test
	@DisplayName("the suspect killing a cop releases the cold-trail backup hold before the squad hears of it")
	void suspectKill_releasesTheBackupHold_beforeManDown() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		Player       suspect = mock(Player.class);
		when(manager.findDyingCop(body)).thenReturn(cop);

		CopGroup group = new CopGroup(UUID.randomUUID());
		when(suspect.getUniqueId()).thenReturn(group.getTargetPlayerId());
		when(body.getKiller()).thenReturn(suspect);
		List<Boolean> heldAtSignal = new ArrayList<>();
		group.setListener((squad, signal, member, where) -> heldAtSignal.add(group.isBackupHeld()));
		group.add(cop);
		group.add(mock(CopNpc.class));
		group.setBackupHeld(true);
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(group.getSquad());

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		assertEquals(List.of(false), heldAtSignal);
	}

	@Test
	@DisplayName("a cop killed by someone other than the suspect keeps the cold-trail hold")
	void otherKiller_keepsTheBackupHold() {
		CopManager   manager = mock(CopManager.class);
		LivingEntity body    = mock(LivingEntity.class);
		CopNpc       cop     = mock(CopNpc.class);
		Player       other   = mock(Player.class);
		when(manager.findDyingCop(body)).thenReturn(cop);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getKiller()).thenReturn(other);

		CopGroup group = new CopGroup(UUID.randomUUID());
		group.add(cop);
		group.add(mock(CopNpc.class));
		group.setBackupHeld(true);
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(group.getSquad());

		new CopListener(manager, mock(HeatLedger.class)).onCopDeath(deathOf(body));

		assertTrue(group.isBackupHeld());
	}

	/** A cop of a group of two whose squad radio is {@code radio}; the dying entity is {@code body}. */
	private static CopNpc copIn(CopManager manager, LivingEntity body, NpcSquadListener radio) {
		CopNpc   cop   = mock(CopNpc.class);
		CopGroup group = new CopGroup(UUID.randomUUID());
		group.setListener(radio);
		group.add(cop);
		group.add(mock(CopNpc.class));
		when(manager.findDyingCop(body)).thenReturn(cop);
		when(manager.findCopByEntity(body)).thenReturn(cop);
		when(manager.isCopNpc(body)).thenReturn(true);
		when(cop.getGroup()).thenReturn(group);
		when(cop.getCurrentSquad()).thenReturn(group.getSquad());
		return cop;
	}

	/** Bartizan's raytrace hit of {@code shooter} on {@code body}. */
	private static WeaponRaytraceImpactEvent shot(LivingEntity body, Player shooter) {
		WeaponRaytraceImpactEvent event = mock(WeaponRaytraceImpactEvent.class);
		when(event.getHitEntity()).thenReturn(body);
		when(event.getShooter()).thenReturn(shooter);
		return event;
	}

	@Test
	@DisplayName("T-544: a player's kill books Kill_Cop at the body, before the squad hears the cop is down")
	void playerKill_booksKillCopAtTheBody_beforeManDown() {
		CopManager       manager = mock(CopManager.class);
		HeatLedger       ledger  = mock(HeatLedger.class);
		LivingEntity     body    = mock(LivingEntity.class);
		Player           killer  = mock(Player.class);
		Location         at      = mock(Location.class);
		NpcSquadListener radio   = mock(NpcSquadListener.class);
		when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
		when(body.getKiller()).thenReturn(killer);
		when(body.getLocation()).thenReturn(at);
		copIn(manager, body, radio);

		new CopListener(manager, ledger).onCopDeath(deathOf(body));

		InOrder order = inOrder(ledger, radio);
		order.verify(ledger).reportCopKill(killer, at);
		order.verify(radio).onSignal(any(), eq(NpcSquadSignal.MAN_DOWN), any(), any());
	}

	@Test
	@DisplayName("T-544: no player to blame (a fall, lava, an NPC): the squad still hears Man_Down, no Kill_Cop is booked")
	void nullKiller_keepsMemberDown_noCase() {
		CopManager       manager = mock(CopManager.class);
		HeatLedger       ledger  = mock(HeatLedger.class);
		LivingEntity     body    = mock(LivingEntity.class);
		NpcSquadListener radio   = mock(NpcSquadListener.class);
		copIn(manager, body, radio);

		new CopListener(manager, ledger).onCopDeath(deathOf(body));

		verify(radio).onSignal(any(), eq(NpcSquadSignal.MAN_DOWN), any(), any());
		verify(ledger, never()).reportCopKill(any(), any());
	}

	@Test
	@DisplayName("T-544 / BZ-EV-20: a death with no killer is blamed on the player who hit the cop within 5 s")
	void bartizanKill_nullKiller_usesLastAttacker() {
		CopManager   manager  = mock(CopManager.class);
		HeatLedger   ledger   = mock(HeatLedger.class);
		LivingEntity body     = mock(LivingEntity.class);
		Player       attacker = mock(Player.class);
		Location     at       = mock(Location.class);
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
		when(attacker.isOnline()).thenReturn(true);
		when(body.getLocation()).thenReturn(at);
		copIn(manager, body, mock(NpcSquadListener.class));
		long[]      now      = {1_000_000L};
		CopListener listener = new CopListener(manager, ledger);
		listener.setClock(() -> now[0]);

		listener.onWeaponRaytraceImpact(shot(body, attacker));
		now[0] += 4_900;
		listener.onCopDeath(deathOf(body));

		verify(ledger).reportCopKill(attacker, at);
		ArgumentCaptor<CopDeathEvent> captor = ArgumentCaptor.forClass(CopDeathEvent.class);
		verify(bukkit.pluginManager()).callEvent(captor.capture());
		assertSame(attacker, captor.getValue().getKiller());
	}

	@Test
	@DisplayName("T-544: a hit more than 5 s before the death blames nobody")
	void lastAttacker_pastFiveSeconds_notBlamed() {
		CopManager   manager  = mock(CopManager.class);
		HeatLedger   ledger   = mock(HeatLedger.class);
		LivingEntity body     = mock(LivingEntity.class);
		Player       attacker = mock(Player.class);
		when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
		when(attacker.isOnline()).thenReturn(true);
		copIn(manager, body, mock(NpcSquadListener.class));
		long[]      now      = {1_000_000L};
		CopListener listener = new CopListener(manager, ledger);
		listener.setClock(() -> now[0]);

		listener.onWeaponRaytraceImpact(shot(body, attacker));
		now[0] += 5_001;
		listener.onCopDeath(deathOf(body));

		verify(ledger, never()).reportCopKill(any(), any());
	}

	private static EntityDeathEvent deathOf(LivingEntity body) {
		List<ItemStack> drops = new ArrayList<>();
		return new EntityDeathEvent(body, drops, 5);
	}

	@Test
	@DisplayName("a player's death ends the hunt as DEATH, so the squad is removed (0.16.1 T-187)")
	void playerDeath_endsTheHuntAsDeath() {
		CopManager        manager = mock(CopManager.class);
		Player            player  = mock(Player.class);
		PlayerDeathEvent  event   = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(player);

		new CopListener(manager, mock(HeatLedger.class)).onPlayerDeath(event);

		verify(manager).onWantedEnd(player, WantedCause.DEATH);
	}

	@Test
	@DisplayName("a downed player ends the hunt as DEATH too (0.16.1 T-187)")
	void playerDowned_endsTheHuntAsDeath() {
		CopManager       manager = mock(CopManager.class);
		Player           player  = mock(Player.class);
		PlayerDownedEvent event  = mock(PlayerDownedEvent.class);
		when(event.getPlayer()).thenReturn(player);

		new CopListener(manager, mock(HeatLedger.class)).onPlayerDowned(event);

		verify(manager).onWantedEnd(player, WantedCause.DEATH);
	}
}
