package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
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
 * The {@code world/x/y/z} placeholder map that {@code jail list} hands to {@link CommandMessages#format} for a row's
 * hover text: the world's name or {@code ?}, the block coordinates, an unmodifiable map.
 */
@DisplayName("jail list location line")
class JailListLocationLineTest {

	private BukkitStatics   bukkit;
	private JailRegistry    registry;
	private CommandMessages messages;
	private CommandSender   sender;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
		registry = mock(JailRegistry.class);
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
	private Map<String, String> hoverMap(Location where) {
		Jail jail = mock(Jail.class);
		when(jail.getId()).thenReturn(4);
		when(jail.getLocation()).thenReturn(where);
		when(registry.getCells()).thenReturn(List.of(jail));

		new JailListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), registry, messages)
				.executeArgument(sender, new String[0]);

		var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
		verify(messages).format(eq(CommandMessages.Key.JAIL_LIST_HOVER), captor.capture());
		return (Map<String, String>) captor.getValue();
	}

	@Test
	@DisplayName("the hover text gets the world name and the block coordinates, in an unmodifiable map")
	void hover_worldAndBlockCoordinates_immutable() {
		Location where = location("world_the_end", -12, 80, 345);

		Map<String, String> hover = hoverMap(where);

		assertEquals(Map.of("world", "world_the_end", "x", "-12", "y", "80", "z", "345"), hover);
		assertThrows(UnsupportedOperationException.class, () -> hover.put("w", "x"));
	}

	@Test
	@DisplayName("a jail whose location has no world prints ?")
	void hover_noWorld_questionMark() {
		Location where = location(null, 1, 2, 3);

		assertEquals(Map.of("world", "?", "x", "1", "y", "2", "z", "3"), hoverMap(where));
	}
}
