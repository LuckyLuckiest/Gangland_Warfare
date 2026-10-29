package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.command.cops.setup.SetupCommand;
import org.luckyraven.gangland.copsncrooks.command.cops.spawner.CopSpawnerCommand;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A bare {@code /glw cop setup} and {@code /glw cop spawner} answer with the Missing Arguments usage, never a pipe list. */
@DisplayName("Bare cop setup and spawner")
class BareCopRootsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SetupFixture fx;

	@BeforeEach
	void setUp() throws IOException {
		fx = new SetupFixture(tempDir);
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return "";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("bare cop setup sends Missing Arguments with the <action> usage")
	void bareSetup_sendsMissingArgumentsUsage() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission(anyString())).thenReturn(true);

		new SetupCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), fx.commands)
				.executeArgument(sender, new String[]{"cop", "setup"});

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(sender).sendMessage(sent.capture());
		String text = ChatColor.stripColor(sent.getValue());
		assertTrue(text.contains("Missing Arguments"), text);
		assertTrue(text.contains("/glw cop setup <action>"), text);
		assertFalse(text.contains("|"), text);
	}

	@Test
	@DisplayName("bare cop spawner sends the <action> usage")
	void bareSpawner_sendsActionUsage() {
		CommandMessages messages = mock(CommandMessages.class);
		when(messages.usage(anyString())).thenAnswer(call -> call.getArgument(0));
		CommandSender sender = mock(CommandSender.class);

		new CopSpawnerCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), null, null, null, messages)
				.executeArgument(sender, new String[]{"cop", "spawner"});

		verify(sender).sendMessage("/glw cop spawner <action>");
	}
}
