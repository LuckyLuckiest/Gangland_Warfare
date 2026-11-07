package org.luckyraven.gangland.command.sub.debug;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-935: {@code /glw timer delete} and a second {@code create} dropped a started timer from the maps without stopping
 * it, so its repeating task kept running with no handle left to stop it.
 */
@DisplayName("/glw timer - create and delete stop the timer they drop")
class TimerCommandLifecycleTest {

	private MockedStatic<Bukkit> bukkit;
	private BukkitTask           task;
	private CommandSender        sender;
	private TimerCommand         command;

	@BeforeEach
	void setUp() {
		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);

		task = mock(BukkitTask.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

		Command.setInformationManager(mock(InformationManager.class));

		sender  = mock(CommandSender.class);
		command = new TimerCommand(mock(JavaPlugin.class));
		command.initializeArguments();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		Command.setInformationManager(null);
	}

	private void run(String label) {
		for (Argument child : command.getArgument().getNode().getChildren().stream().map(n -> n.getData()).toList()) {
			if (child.getArguments()[0].equals(label)) {
				child.executeArgument(sender, new String[]{"timer", label});
				return;
			}
		}
		throw new AssertionError("no sub-argument " + label);
	}

	@Test
	@DisplayName("delete stops a started timer")
	void deleteStopsTheStartedTimer() {
		run("create");
		run("start");
		verify(task, never()).cancel();

		run("delete");

		verify(task).cancel();
	}

	@Test
	@DisplayName("creating again stops the started timer it replaces")
	void createStopsTheTimerItReplaces() {
		run("create");
		run("start");

		run("create");

		verify(task).cancel();
	}

	@Test
	@DisplayName("delete with no timer does not fail")
	void deleteWithNoTimer() {
		run("delete");

		verify(task, never()).cancel();
	}
}
