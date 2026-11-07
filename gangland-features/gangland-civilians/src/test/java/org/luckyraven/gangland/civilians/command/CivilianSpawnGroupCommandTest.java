package org.luckyraven.gangland.civilians.command;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianGroup;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.config.CivilianGroupConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CivilianSpawnGroupCommand")
class CivilianSpawnGroupCommandTest {

	private BukkitStatics     bukkit;
	private CivilianService   service;
	private CivilianMessages  messages;
	private Player            player;
	private Argument          groupArgument;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction

		CiviliansConfig config = mock(CiviliansConfig.class);
		when(config.groups()).thenReturn(Map.of("gang_patrol", mock(CivilianGroupConfig.class)));

		service = mock(CivilianService.class);
		when(service.getCiviliansConfig()).thenReturn(config);

		messages = mock(CivilianMessages.class);
		when(messages.groupSpawned("gang_patrol")).thenReturn("spawned");
		when(messages.spawnFailed("gang_patrol")).thenReturn("failed");

		player = mock(Player.class);
		when(player.getLocation()).thenReturn(mock(Location.class));

		groupArgument = new CivilianSpawnGroupCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                              service, messages).getNode().getChildren().get(0).getData();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a group that spawned is reported as spawned")
	void spawnGroup_spawned_reportsSuccess() {
		when(service.spawnGroup(any(), any())).thenReturn(mock(CivilianGroup.class));

		groupArgument.executeArgument(player, new String[]{"civilian", "spawngroup", "gang_patrol"});

		verify(player).sendMessage("spawned");
		verify(player, never()).sendMessage("failed");
	}

	@Test
	@DisplayName("T-946: a group that came out empty (spawnGroup returned null) is reported as a failure")
	void spawnGroup_nothingSpawned_reportsFailureNotSuccess() {
		when(service.spawnGroup(any(), any())).thenReturn(null);

		groupArgument.executeArgument(player, new String[]{"civilian", "spawngroup", "gang_patrol"});

		verify(player).sendMessage("failed");
		verify(player, never()).sendMessage("spawned");
	}
}
