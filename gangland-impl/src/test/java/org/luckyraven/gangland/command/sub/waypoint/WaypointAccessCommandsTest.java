package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-827 / T-828: {@code /glw waypoint info} and {@code select} are open to players, so each must apply
 * {@code WaypointAccess} itself (LS-01), and {@code select} must take a name as well as an id.
 */
@DisplayName("waypoint info and select - access check and select-by-name")
class WaypointAccessCommandsTest {

	@TempDir
	static Path tempDir;

	private UserManager<Player> userManager;
	private WaypointManager     waypointManager;
	private Player              player;
	private User<Player>        user;
	private Waypoint            hub;
	private Waypoint            rival;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Prefix", "")
				              .withString("Errors.Waypoint.Invalid", "INVALID")
				              .withString("Errors.Must_Be_Numbers", "NUMBERS")
				              .withString("Errors.Permissions.Warp", "NOPERM"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		userManager     = mock(UserManager.class);
		waypointManager = mock(WaypointManager.class);
		player          = mock(Player.class);
		user            = mock(User.class);

		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);
		when(userManager.getUser(player)).thenReturn(user);

		hub = new Waypoint("Hub", "gangland");
		hub.setUsedId(1);
		rival = new Waypoint("Rival", "gangland");
		rival.setUsedId(2);

		when(player.hasPermission(hub.getPermission())).thenReturn(true);
		when(player.hasPermission(rival.getPermission())).thenReturn(false);

		when(waypointManager.get("Hub")).thenReturn(hub);
		when(waypointManager.get("Rival")).thenReturn(rival);
		when(waypointManager.get(1)).thenReturn(hub);
		when(waypointManager.get(2)).thenReturn(rival);
		when(waypointManager.getWaypoints()).thenReturn(Map.of(1, hub, 2, rival));
	}

	@Test
	@DisplayName("info on a restricted waypoint shows no coordinates and answers as if it does not exist")
	void info_restricted_refused() {
		WaypointInfoCommand.show(player, userManager, waypointManager, "Rival");

		verify(player).sendMessage(contains("INVALID"));
		verify(player, never()).sendMessage(contains("X:"));
		verify(player, never()).spigot();
	}

	@Test
	@DisplayName("info on an accessible waypoint shows the coordinates")
	void info_accessible_shown() {
		when(player.spigot()).thenReturn(mock(Player.Spigot.class));

		WaypointInfoCommand.show(player, userManager, waypointManager, "Hub");

		verify(player).sendMessage(contains("X:"));
	}

	@Test
	@DisplayName("the console keeps the full info view")
	void info_console_full() {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);
		when(console.spigot()).thenReturn(mock(org.bukkit.command.CommandSender.Spigot.class));

		WaypointInfoCommand.show(console, userManager, waypointManager, "Rival");

		verify(console).sendMessage(contains("X:"));
	}

	@Test
	@DisplayName("info suggestions leave out waypoints the player cannot reach")
	void info_suggest_filtered() {
		assertEquals(List.of("Hub"), WaypointInfoCommand.suggest(player, userManager, waypointManager));
		assertEquals(List.of("Hub", "Rival"),
		             WaypointInfoCommand.suggest(mock(ConsoleCommandSender.class), userManager, waypointManager));
	}

	@Test
	@DisplayName("T-828: select accepts a waypoint name")
	void select_byName() {
		WaypointSelectCommand.select(userManager, waypointManager, player, "Hub");

		verify(waypointManager).playerSelect(player, hub);
	}

	@Test
	@DisplayName("select still accepts a numeric id")
	void select_byId() {
		WaypointSelectCommand.select(userManager, waypointManager, player, "1");

		verify(waypointManager).playerSelect(player, hub);
	}

	@Test
	@DisplayName("select by name still runs the access check")
	void select_byName_restricted() {
		WaypointSelectCommand.select(userManager, waypointManager, player, "Rival");

		verify(waypointManager, never()).playerSelect(eq(player), org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("select of an unknown name says invalid waypoint, not must-be-numbers")
	void select_unknownName() {
		WaypointSelectCommand.select(userManager, waypointManager, player, "Nowhere");

		verify(player).sendMessage(contains("INVALID"));
		verify(player, never()).sendMessage(contains("NUMBERS"));
	}
}
