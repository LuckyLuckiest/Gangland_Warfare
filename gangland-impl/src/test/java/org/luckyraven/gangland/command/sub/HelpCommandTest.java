package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * gi=90: {@code HelpCommand} snapshotted its aggregate list once in the constructor. It is built during the CORE
 * package scan ({@code GanglandContext.runCommandPhase()}, LOWEST priority so it is last <em>within that scan</em>),
 * but every module's own commands register in separate, later {@code scanAndRegisterCommands} calls - so any
 * command registered after HelpCommand's own construction (i.e. every module command) never made it into
 * {@code /glw help}.
 */
@DisplayName("HelpCommand")
class HelpCommandTest {

	@org.junit.jupiter.api.io.TempDir
	static Path tempDir;

	private JavaPlugin         plugin;
	private CommandManager     commandManager;
	private InformationManager informationManager;
	private CommandSender      sender;
	private BukkitStatics      bukkit;

	@BeforeEach
	void setUp() {
		SettingsFixture.initializeMinimal(tempDir);
		bukkit = BukkitStatics.install(); // Command's Argument tree registers a Bukkit permission on construction

		plugin              = mock(JavaPlugin.class);
		commandManager      = new CommandManager(plugin, mock(DependencyContainer.class), "gangland", "glw");
		informationManager  = new InformationManager();
		informationManager.processCommands();
		sender              = mock(CommandSender.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("help() reflects a command registered after HelpCommand's own construction (a later module scan)")
	void help_includesCommandsRegisteredAfterConstruction() {
		HelpCommand helpCommand = new HelpCommand(plugin, informationManager, commandManager);
		commandManager.addCommand(helpCommand);

		// Simulates a module's own scanAndRegisterCommands call, which always runs after the core scan that just
		// built helpCommand above (GanglandContext.java L286-292).
		StubCommand moduleCommand = new StubCommand(plugin, "turf",
				new CommandInformation("/glw turf", "Turf module command."));
		commandManager.addCommand(moduleCommand);

		helpCommand.renderHelp(sender, 1);

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(sent.capture());
		List<String> lines = sent.getAllValues();
		assertTrue(lines.stream().anyMatch(line -> line.contains("turf")),
		           "help() must include commands registered after HelpCommand's own construction; got " + lines);
	}

	/** Minimal stand-in for a module's own top-level {@link Command}, carrying one fixed help entry. */
	private static final class StubCommand extends Command {

		StubCommand(JavaPlugin plugin, String label, CommandInformation info) {
			super(plugin, label, false);
			getHelpInfo().add(info);
		}

		@Override
		protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) { }

		@Override
		protected void initializeArguments() { }

		@Override
		protected void help(CommandSender sender, int page) { }
	}
}
