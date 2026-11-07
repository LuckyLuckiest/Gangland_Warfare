package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.database.tables.waypoint.WaypointTable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.database.DatabaseHelper;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.mockito.MockedConstruction;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WaypointDeleteCommand - pending delete confirmation")
class WaypointDeleteConfirmTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Invalid_Waypoint", "INVALID-WAYPOINT")
				              .withString("Commands.Waypoint.Deleted", "DELETED"));
	}

	@Test
	@DisplayName("ids compacted between the request and the confirm: the waypoint asked for is deleted, not whoever took its id")
	@SuppressWarnings("unchecked")
	void confirm_afterIdsAreCompacted_deletesTheRequestedWaypoint() {
		JavaPlugin          plugin          = mock(JavaPlugin.class);
		Tree<Argument>      tree            = new Tree<>();
		Argument            parent          = new Argument(plugin, "waypoint", tree);
		tree.add(parent.getNode());
		UserManager<Player> userManager     = mock(UserManager.class);
		WaypointManager     waypointManager = mock(WaypointManager.class);
		GanglandDatabase    database        = mock(GanglandDatabase.class);
		PermissionManager   permissions     = mock(PermissionManager.class);
		Player              sender          = mock(Player.class);

		Waypoint asked    = mock(Waypoint.class);
		Waypoint neighbor = mock(Waypoint.class);

		when(asked.getUsedId()).thenReturn(5);
		when(neighbor.getUsedId()).thenReturn(6);
		when(waypointManager.get(5)).thenReturn(asked);
		when(database.getTables()).thenReturn(List.of(new WaypointTable()));

		try (MockedConstruction<CountdownTimer> timers = mockConstruction(CountdownTimer.class);
		     MockedConstruction<DatabaseHelper> helpers = mockConstruction(DatabaseHelper.class)) {
			Argument command = new WaypointDeleteCommand(plugin, tree, parent, userManager, waypointManager, database,
			                                             permissions);

			ConfirmArgument confirm = (ConfirmArgument) command.getNode().getChildren().stream()
					.map(node -> node.getData()).filter(ConfirmArgument.class::isInstance).findFirst().orElseThrow();
			Argument optional = command.getNode().getChildren().stream()
					.map(node -> node.getData()).filter(OptionalArgument.class::isInstance).findFirst().orElseThrow();

			optional.getAction().accept(optional, sender, new String[]{"waypoint", "delete", "5"});

			// another admin deletes id 3 and confirms: the ids behind it are compacted by one
			when(asked.getUsedId()).thenReturn(4);
			when(neighbor.getUsedId()).thenReturn(5);
			when(waypointManager.get(4)).thenReturn(asked);
			when(waypointManager.get(5)).thenReturn(neighbor);

			confirm.getAction().accept(confirm, sender, new String[]{"waypoint", "delete", "confirm"});
		}

		verify(waypointManager, never()).remove(neighbor);
		verify(waypointManager).remove(asked);
	}
}
