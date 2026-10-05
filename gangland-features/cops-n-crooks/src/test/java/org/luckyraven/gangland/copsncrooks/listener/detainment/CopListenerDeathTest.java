package org.luckyraven.gangland.copsncrooks.listener.detainment;

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
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
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

		new CopListener(manager).onCopDeath(deathOf(body));

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

		new CopListener(manager).onCopDeath(deathOf(body));

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

		new CopListener(manager).onCopDeath(deathOf(body));

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

		new CopListener(manager).onCopDeath(deathOf(body));

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

		new CopListener(manager).onCopDeath(event);

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

		new CopListener(manager).onCopDeath(deathOf(body));

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

		new CopListener(manager).onCopDeath(deathOf(body));

		ArgumentCaptor<CopDeathEvent> captor = ArgumentCaptor.forClass(CopDeathEvent.class);
		verify(bukkit.pluginManager()).callEvent(captor.capture());
		assertNull(captor.getValue().getKiller());
	}

	private static EntityDeathEvent deathOf(LivingEntity body) {
		List<ItemStack> drops = new ArrayList<>();
		return new EntityDeathEvent(body, drops, 5);
	}
}
