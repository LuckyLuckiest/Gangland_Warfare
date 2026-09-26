package org.luckyraven.gangland.command.sub.permissions;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.testkit.BukkitStatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * gi=89: the constructor called {@code getHelpInfo().addAll(list)} twice - a literal copy-paste duplicate line -
 * so every {@code permissions.*} help entry appeared twice in {@code /glw help}.
 */
@DisplayName("PermissionsCommand")
class PermissionsCommandTest {

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

	@Test
	@DisplayName("getHelpInfo lists every permissions.* commands.json entry exactly once, not twice")
	void constructor_populatesHelpInfoOnce() {
		PermissionsCommand command = new PermissionsCommand(mock(JavaPlugin.class), mock(PermissionManager.class));

		long expected = command.getCommands().keySet().stream()
				.filter(key -> key.startsWith("permissions"))
				.count();

		assertEquals(expected, command.getHelpInfo().size(), "duplicate addAll call doubles the help list");
	}
}
