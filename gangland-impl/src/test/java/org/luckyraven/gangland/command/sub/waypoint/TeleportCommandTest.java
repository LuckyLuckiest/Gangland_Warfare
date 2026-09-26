package org.luckyraven.gangland.command.sub.waypoint;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * gi=89: {@code TeleportCommand}'s help-info filter was copied verbatim from {@link WaypointCommand} - it filtered
 * {@code getCommands()} by {@code startsWith("waypoint")} instead of its own registered name ("teleport"), so it
 * re-collected WaypointCommand's own 17 {@code waypoint_*} help entries, duplicating them in {@code /glw help}.
 */
@DisplayName("TeleportCommand")
class TeleportCommandTest {

	private BukkitStatics bukkit;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install(); // Command's Argument tree registers a Bukkit permission on construction
		InformationManager manager = new InformationManager();
		manager.processCommands();
		Command.setInformationManager(manager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("getHelpInfo collects only its own teleport_* entries, not WaypointCommand's waypoint_* entries")
	void constructor_collectsOwnEntriesOnly() {
		TeleportCommand command = new TeleportCommand(mock(JavaPlugin.class), mock(UserManager.class),
				mock(WaypointManager.class));

		long expected = command.getCommands().keySet().stream()
				.filter(key -> key.startsWith("teleport"))
				.count();
		List<String> usages = command.getHelpInfo().getList().stream().map(CommandInformation::usage).toList();

		assertEquals(expected, command.getHelpInfo().size());
		assertTrue(usages.stream().noneMatch(usage -> usage.contains("waypoint")),
		           "must not re-collect WaypointCommand's own help entries; got " + usages);
	}
}
