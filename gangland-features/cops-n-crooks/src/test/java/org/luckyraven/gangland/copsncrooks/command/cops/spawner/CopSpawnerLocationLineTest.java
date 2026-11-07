package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The {@code world/x/y/z} placeholder map that {@code spawner info} and {@code spawner list} hand to
 * {@link CommandMessages#format}: the world's name or {@code ?}, the block coordinates, an unmodifiable map.
 */
@DisplayName("cop spawner location line")
class CopSpawnerLocationLineTest {

	private BukkitStatics   bukkit;
	private CopSpawnManager manager;
	private CommandMessages messages;
	private CommandSender   sender;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
		manager  = mock(CopSpawnManager.class);
		messages = mock(CommandMessages.class);
		sender   = mock(CommandSender.class);

		when(messages.format(any(), anyMap())).thenReturn("line");
		when(sender.spigot()).thenReturn(mock(CommandSender.Spigot.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private static Location location(String worldName, int x, int y, int z) {
		Location location = mock(Location.class);
		if (worldName != null) {
			World world = mock(World.class);
			when(world.getName()).thenReturn(worldName);
			when(location.getWorld()).thenReturn(world);
		}
		when(location.getBlockX()).thenReturn(x);
		when(location.getBlockY()).thenReturn(y);
		when(location.getBlockZ()).thenReturn(z);
		return location;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> captured(CommandMessages.Key key) {
		var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
		verify(messages).format(eq(key), captor.capture());
		return (Map<String, String>) captor.getValue();
	}

	private void runInfo(String id) {
		Argument info  = new CopSpawnerInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), manager,
		                                           messages);
		Argument idArg = info.getNode().getChildren().get(0).getData();
		idArg.getAction().accept(idArg, sender, new String[]{"cop", "spawner", "info", id});
	}

	private void runList() {
		new CopSpawnerListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), manager, messages)
				.executeArgument(sender, new String[0]);
	}

	@Test
	@DisplayName("info: the line carries the world name and the block coordinates")
	void info_line_worldAndBlockCoordinates() {
		Location where = location("world_nether", 11, 64, -3);
		when(manager.getSpawnerLocation(7)).thenReturn(where);

		runInfo("7");

		assertEquals(Map.of("world", "world_nether", "x", "11", "y", "64", "z", "-3"),
		             captured(CommandMessages.Key.SPAWNER_INFO_LINE));
	}

	@Test
	@DisplayName("info: a location without a world prints ? and the map is unmodifiable")
	void info_line_noWorld_questionMark_immutable() {
		Location where = location(null, 0, 1, 2);
		when(manager.getSpawnerLocation(7)).thenReturn(where);

		runInfo("7");

		Map<String, String> line = captured(CommandMessages.Key.SPAWNER_INFO_LINE);
		assertEquals(Map.of("world", "?", "x", "0", "y", "1", "z", "2"), line);
		assertThrows(UnsupportedOperationException.class, () -> line.put("w", "x"));
	}

	@Test
	@DisplayName("list: the hover text gets the same map, with ? for a missing world")
	void list_hover_worldAndBlockCoordinates() {
		CopSpawner spawner = mock(CopSpawner.class);
		when(spawner.getId()).thenReturn(3);
		Location where = location("world", -5, 70, 9);
		when(spawner.getLocation()).thenReturn(where);
		when(manager.getSpawners()).thenReturn(List.of(spawner));

		runList();

		Map<String, String> hover = captured(CommandMessages.Key.SPAWNER_LIST_HOVER);
		assertEquals(Map.of("world", "world", "x", "-5", "y", "70", "z", "9"), hover);
		assertThrows(UnsupportedOperationException.class, () -> hover.put("w", "x"));
	}

	@Test
	@DisplayName("list: a spawner whose location has no world prints ?")
	void list_hover_noWorld_questionMark() {
		CopSpawner spawner = mock(CopSpawner.class);
		when(spawner.getId()).thenReturn(3);
		Location where = location(null, 1, 2, 3);
		when(spawner.getLocation()).thenReturn(where);
		when(manager.getSpawners()).thenReturn(List.of(spawner));

		runList();

		assertEquals(Map.of("world", "?", "x", "1", "y", "2", "z", "3"),
		             captured(CommandMessages.Key.SPAWNER_LIST_HOVER));
	}
}
