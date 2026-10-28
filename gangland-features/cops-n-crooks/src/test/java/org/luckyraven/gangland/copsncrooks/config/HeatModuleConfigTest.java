package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the heat wiring: the module registers a cop-attacked hook that reports the assault to the ledger one tick later
 * (0.21.2, T-535/T-536), so a lethal blow books only Kill_Cop and the hit cop's own sighting is in by then.
 */
@DisplayName("HeatModuleConfig")
class HeatModuleConfigTest {

	private CopManager       manager;
	private HeatLedger       ledger;
	private JavaPlugin       plugin;
	private BukkitScheduler  scheduler;
	private HeatModuleConfig config;

	@BeforeEach
	void setUp() {
		manager   = mock(CopManager.class);
		ledger    = mock(HeatLedger.class);
		plugin    = mock(JavaPlugin.class);
		scheduler = mock(BukkitScheduler.class);
		Server server = mock(Server.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopManager.class, manager);
		container.registerInstance(HeatLedger.class, ledger);

		config = new HeatModuleConfig(plugin, container);
	}

	/** Runs the task the hook scheduled for the next tick. */
	private void nextTick() {
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTask(eq(plugin), task.capture());
		task.getValue().run();
	}

	private LivingEntity copBody(UUID copId) {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(copId);
		when(entity.isValid()).thenReturn(true);
		return entity;
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<CopNpc, Player> registeredHook() {
		config.registerAttackedHook();

		ArgumentCaptor<BiConsumer<CopNpc, Player>> captor = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addCopAttackedHook(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("postConstruct registers an attacked hook that reports the assault on the next tick, where he hit from")
	void postConstruct_registersAnAttackedHookThatReportsTheAssault() {
		UUID         copId    = UUID.randomUUID();
		LivingEntity entity   = copBody(copId);
		CopNpc       cop      = mock(CopNpc.class);
		Player       player   = mock(Player.class);
		Location     location = mock(Location.class);
		when(cop.getEntity()).thenReturn(entity);
		when(player.getLocation()).thenReturn(location, mock(Location.class)); // he moves before the next tick
		when(player.isOnline()).thenReturn(true);

		registeredHook().accept(cop, player);
		nextTick();

		verify(ledger).reportAssault(player, copId, location);
	}

	@Test
	@DisplayName("T-535: a lethal hit books no Assault_Cop: by the next tick the cop is dead, and Kill_Cop is his death's")
	void lethalHitOnCop_commitsOnlyKillCop() {
		LivingEntity entity = copBody(UUID.randomUUID());
		CopNpc       cop    = mock(CopNpc.class);
		Player       player = mock(Player.class);
		when(cop.getEntity()).thenReturn(entity);
		when(player.getLocation()).thenReturn(mock(Location.class));
		when(player.isOnline()).thenReturn(true);

		registeredHook().accept(cop, player);
		when(entity.isDead()).thenReturn(true);
		when(entity.isValid()).thenReturn(false);
		nextTick();

		verify(ledger, never()).reportAssault(any(), any(), any());
	}

	@Test
	@DisplayName("T-536: the hook reports nothing in the hit's own tick, so the hit cop's sighting (CopManager) lands first")
	void assaultHook_runsAfterSquadSighting() {
		LivingEntity entity = copBody(UUID.randomUUID());
		CopNpc       cop    = mock(CopNpc.class);
		Player       player = mock(Player.class);
		when(cop.getEntity()).thenReturn(entity);
		when(player.getLocation()).thenReturn(mock(Location.class));
		when(player.isOnline()).thenReturn(true);

		registeredHook().accept(cop, player);

		verify(ledger, never()).reportAssault(any(), any(), any());
		nextTick();
		verify(ledger).reportAssault(eq(player), any(), any());
	}

	@Test
	@DisplayName("the hook is registered by the bean container: registerAttackedHook is a @PostConstruct")
	void registerAttackedHook_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(HeatModuleConfig.class.getMethod("registerAttackedHook").isAnnotationPresent(PostConstruct.class),
		           "without it, hitting a cop never reaches the heat ledger");
	}

	@Test
	@DisplayName("a cop without an entity reports nothing")
	void attackedHook_copWithoutEntity_reportsNothing() {
		CopNpc cop = mock(CopNpc.class);
		when(cop.getEntity()).thenReturn(null);

		registeredHook().accept(cop, mock(Player.class));

		verifyNoInteractions(ledger);
	}
}
