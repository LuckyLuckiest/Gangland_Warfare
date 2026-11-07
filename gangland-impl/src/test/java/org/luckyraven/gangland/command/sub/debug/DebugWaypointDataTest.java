package org.luckyraven.gangland.command.sub.debug;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.JsonFormatter;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.permission.PermissionManager;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterises {@code /glw debug waypoint-data}: a player receives every waypoint's {@code toString()} converted to
 * indented JSON, any other sender (the console) receives the raw {@code toString()}, one message per waypoint in map
 * order.
 */
@DisplayName("/glw debug waypoint-data")
class DebugWaypointDataTest {

	@TempDir
	static Path tempDir;

	private WaypointManager waypointManager;
	private Argument        waypointData;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Prefix", ""));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		Command.setInformationManager(mock(InformationManager.class));

		waypointManager = mock(WaypointManager.class);
		DebugCommand command = new DebugCommand(mock(Gangland.class), mock(UserManager.class), waypointManager,
		                                        mock(PermissionManager.class), mock(GanglandPlaceholder.class),
		                                        mock(CommandManager.class),
		                                        mock(org.luckyraven.gangland.menu.villager.VillagerInventoryRegistry.class),
		                                        mock(DependencyContainer.class), mock(InventoryService.class));
		command.initializeArguments();

		waypointData = command.getArgument().getNode().getChildren().stream()
				.map(Tree.Node::getData)
				.filter(argument -> Arrays.asList(argument.getArguments()).contains("waypoint-data"))
				.findFirst()
				.orElseThrow();
	}

	@AfterEach
	void tearDown() {
		Command.setInformationManager(null);
	}

	private static Waypoint waypoint(String text) {
		Waypoint waypoint = mock(Waypoint.class);
		when(waypoint.toString()).thenReturn(text);
		return waypoint;
	}

	private void withWaypoints(String... texts) {
		Map<Integer, Waypoint> all = new LinkedHashMap<>();
		for (int index = 0; index < texts.length; index++) {
			all.put(index, waypoint(texts[index]));
		}
		when(waypointManager.getWaypoints()).thenReturn(all);
	}

	@Test
	@DisplayName("a player gets each waypoint as indented JSON, in map order")
	void player_getsJson() {
		withWaypoints("{\"name\":\"hq\",\"id\":1}", "{\"name\":\"dock\"}");
		Player player = mock(Player.class);

		waypointData.executeArgument(player, new String[]{"debug", "waypoint-data"});

		JsonFormatter formatter = new JsonFormatter();
		var           order     = inOrder(player);
		order.verify(player).sendMessage(formatter.formatToJson("{\"name\":\"hq\",\"id\":1}", "   "));
		order.verify(player).sendMessage(formatter.formatToJson("{\"name\":\"dock\"}", "   "));
		verifyNoMoreInteractions(player);
	}

	@Test
	@DisplayName("the console gets each waypoint's raw toString, in map order")
	void console_getsRawText() {
		withWaypoints("{\"name\":\"hq\",\"id\":1}", "{\"name\":\"dock\"}");
		CommandSender console = mock(CommandSender.class);

		waypointData.executeArgument(console, new String[]{"debug", "waypoint-data"});

		var order = inOrder(console);
		order.verify(console).sendMessage("{\"name\":\"hq\",\"id\":1}");
		order.verify(console).sendMessage("{\"name\":\"dock\"}");
		verifyNoMoreInteractions(console);
	}

	@Test
	@DisplayName("no waypoints: nothing is sent to either kind of sender")
	void empty_sendsNothing() {
		withWaypoints();
		Player        player  = mock(Player.class);
		CommandSender console = mock(CommandSender.class);

		waypointData.executeArgument(player, new String[]{"debug", "waypoint-data"});
		waypointData.executeArgument(console, new String[]{"debug", "waypoint-data"});

		verifyNoMoreInteractions(player, console);
	}
}
