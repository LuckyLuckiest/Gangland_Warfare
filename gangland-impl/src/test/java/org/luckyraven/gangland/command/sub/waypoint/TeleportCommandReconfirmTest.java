package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

@DisplayName("TeleportCommand - costed teleport confirmation window")
class TeleportCommandReconfirmTest {

	@TempDir
	static Path tempDir;

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		InformationManager manager = new InformationManager();
		manager.processCommands();
		Command.setInformationManager(manager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider().withString("Errors.Prefix", ""));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("when the confirm window lapses the pending timer entry is dropped with the pending waypoint")
	void lapsedConfirmWindow_dropsTheTimerEntry() throws Exception {
		UserManager<Player> userManager     = mock(UserManager.class);
		WaypointManager     waypointManager = mock(WaypointManager.class);
		Player              player          = mock(Player.class);
		User<Player>        user            = mock(User.class);
		Waypoint            waypoint        = mock(Waypoint.class);

		when(user.getUser()).thenReturn(player);
		when(userManager.getUser(player)).thenReturn(user);
		when(waypointManager.getSelected(player)).thenReturn(waypoint);
		when(waypoint.getCost()).thenReturn(50D);
		when(waypoint.getPermission()).thenReturn("gangland.waypoint.test");
		when(player.hasPermission("gangland.waypoint.test")).thenReturn(true);

		List<Consumer<CountdownTimer>> expiry = new ArrayList<>();

		try (MockedConstruction<CountdownTimer> timers = mockConstruction(CountdownTimer.class, (timer, context) ->
				expiry.add((Consumer<CountdownTimer>) context.arguments().get(4)))) {
			TeleportCommand command = new TeleportCommand(mock(JavaPlugin.class), userManager, waypointManager);

			command.onExecute(null, player, new String[]{"teleport"});

			assertEquals(1, expiry.size(), "the first costed tp must open exactly one confirm window");
			assertEquals(1, pending(command, "reconfirmTimer").size());

			expiry.get(0).accept(timers.constructed().get(0));

			assertTrue(pending(command, "reconfirm").isEmpty(), "the pending waypoint is dropped on expiry");
			assertTrue(pending(command, "reconfirmTimer").isEmpty(),
			           "the lapsed timer must not stay mapped to the player: " + pending(command, "reconfirmTimer"));
		}
	}

	private static Map<?, ?> pending(TeleportCommand command, String field) throws Exception {
		Field f = TeleportCommand.class.getDeclaredField(field);
		f.setAccessible(true);
		return (Map<?, ?>) f.get(command);
	}
}
