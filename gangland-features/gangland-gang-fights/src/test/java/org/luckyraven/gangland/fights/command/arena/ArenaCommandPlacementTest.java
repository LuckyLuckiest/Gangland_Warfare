package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaDrafts;
import org.luckyraven.gangland.fights.arena.ArenaMessages;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the two arena-point conversions of the arena commands: {@code /glw arena tp <name>} builds the Bukkit
 * {@code Location} of the arena exit, and {@code ArenaDeps.point} (used by {@code exit} and {@code spawn}) builds the
 * {@code ArenaPoint} of a Location, with an empty world name when the Location has no world.
 */
class ArenaCommandPlacementTest {

	private static final ArenaPoint EXIT = new ArenaPoint("w", 1.5, 2.5, 3.5, 4.5f, 5.5f);

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;
	private World                  world;
	private ArenaRegistry          registry;
	private ArenaCommand           command;

	@BeforeEach
	void setUp() {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		world = mock(World.class);
		when(world.getName()).thenReturn("w");
		bukkit.when(() -> Bukkit.getWorld("w")).thenReturn(world);

		Command.setInformationManager(mock(InformationManager.class));

		registry = mock(ArenaRegistry.class);
		ArenaMessages text = mock(ArenaMessages.class);
		when(text.text(anyString(), any(Object[].class))).thenAnswer(call -> {
			Object[] all = call.getArguments();
			return call.getArgument(0) + Arrays.toString(Arrays.copyOfRange(all, 1, all.length));
		});
		command = new ArenaCommand(mock(JavaPlugin.class), registry, new ArenaDrafts(), text, mock(FightRules.class));
		command.initializeArguments();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	private void runTp(Player sender, String name) {
		for (Tree.Node<Argument> child : command.getArgument().getNode().getChildren()) {
			if (!child.getData().getArguments()[0].equals("tp")) continue;

			for (Tree.Node<Argument> operand : child.getChildren()) {
				operand.getData().executeArgument(sender, new String[]{"arena", "tp", name});
			}
		}
	}

	private static Arena arenaWithExit(ArenaPoint exit) {
		return new Arena("pit", null, List.of(), List.of(), exit, true);
	}

	@Test
	@DisplayName("arena tp teleports the player to the exit Location and reports Tp with the arena name")
	void tp_teleportsToTheExit() {
		Player player = mock(Player.class);
		when(registry.byName("pit")).thenReturn(Optional.of(arenaWithExit(EXIT)));

		runTp(player, "pit");

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(player).teleport(to.capture());
		assertSame(world, to.getValue().getWorld());
		assertEquals(1.5, to.getValue().getX());
		assertEquals(2.5, to.getValue().getY());
		assertEquals(3.5, to.getValue().getZ());
		assertEquals(4.5f, to.getValue().getYaw());
		assertEquals(5.5f, to.getValue().getPitch());
		verify(player).sendMessage("Arenas.Tp[name, pit]");
	}

	@Test
	@DisplayName("arena tp with the exit world not loaded reports Tp_World_Missing and does not teleport")
	void tp_worldMissing_reportsIt() {
		Player player = mock(Player.class);
		when(registry.byName("pit")).thenReturn(Optional.of(arenaWithExit(new ArenaPoint("gone", 1, 2, 3, 4f, 5f))));

		runTp(player, "pit");

		verify(player, never()).teleport(any(Location.class));
		verify(player).sendMessage("Arenas.Tp_World_Missing[world, gone]");
	}

	@Test
	@DisplayName("ArenaDeps.point copies the six components and the world name of a Location")
	void point_copiesTheLocation() {
		assertEquals(EXIT, ArenaDeps.point(new Location(world, 1.5, 2.5, 3.5, 4.5f, 5.5f)));
	}

	@Test
	@DisplayName("ArenaDeps.point gives an empty world name for a Location with no world")
	void point_noWorld_hasAnEmptyWorldName() {
		assertEquals(new ArenaPoint("", 1.5, 2.5, 3.5, 4.5f, 5.5f),
		             ArenaDeps.point(new Location(null, 1.5, 2.5, 3.5, 4.5f, 5.5f)));
	}
}
