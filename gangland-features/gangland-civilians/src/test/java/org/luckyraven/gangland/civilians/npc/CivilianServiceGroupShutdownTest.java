package org.luckyraven.gangland.civilians.npc;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.config.CivilianGroupConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-947: {@code spawnGroup} registers the group at once but its members one tick later (so Citizens can finish
 * initialising them). A shutdown/reload in between used to destroy only the registered NPCs, so the group's members
 * were registered into the cleared registry afterwards, with no group left that a spawner could despawn them through.
 */
@DisplayName("CivilianService - a group spawned in the same tick as a shutdown")
class CivilianServiceGroupShutdownTest {

	private static final String GROUP_ID = "street_gang";
	private static final String TYPE_ID  = "thug";

	private CivilianNpcRegistry registry;
	private CivilianService     service;
	private BukkitScheduler     scheduler;
	private CivilianNpc         member;

	@BeforeEach
	void setUp() {
		scheduler = mock(BukkitScheduler.class);
		Server server = mock(Server.class);
		when(server.getScheduler()).thenReturn(scheduler);
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getServer()).thenReturn(server);

		CivilianGroupConfig groupConfig = mock(CivilianGroupConfig.class);
		when(groupConfig.members()).thenReturn(Map.of(TYPE_ID, 1));
		CivilianTypeConfig typeConfig = mock(CivilianTypeConfig.class);

		member = mock(CivilianNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
		when(member.getEntity()).thenReturn(entity);
		when(member.isValid()).thenReturn(true);
		// a destroyed NPC is no longer valid, as with the real one
		doAnswer(invocation -> {
			when(member.isValid()).thenReturn(false);
			return null;
		}).when(member).destroy(any());

		CivilianNpcFactory factory = mock(CivilianNpcFactory.class);
		when(factory.createCivilian(any(), any(), any(), any())).thenReturn(member);

		registry = new CivilianNpcRegistry();
		service  = new CivilianService(plugin, mock(CiviliansLoader.class), mock(NpcMarkManager.class),
		                               mock(CivilianSettings.class), factory, mock(CivilianSpawnManager.class),
		                               registry, mock(CivilianMessages.class));
		service.civiliansConfig = new CiviliansConfig(List.of(), List.of(), Map.of(TYPE_ID, typeConfig),
		                                              Map.of(GROUP_ID, groupConfig), true, 20,
		                                              CiviliansConfig.DEFAULT_SHOUTS);
	}

	@Test
	@DisplayName("shutdown before the deferred registration destroys the group's members and nothing re-registers")
	void shutdownBeforeDeferredRegistration_destroysMembersAndLeavesRegistryEmpty() {
		service.spawnGroup(mock(Location.class), GROUP_ID);
		ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTaskLater(any(JavaPlugin.class), deferred.capture(), anyLong());

		service.shutdown();
		deferred.getValue().run();

		verify(member, times(1)).destroy(any());
		assertTrue(registry.getActiveNpcs().isEmpty(), "a member of a group spawned before the shutdown outlived it");
	}
}
