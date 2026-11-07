package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
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
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code /glw arena on <name>} and {@code /glw arena off <name>} look the arena up, flip its enabled flag through the
 * registry by the arena's own name and tell the sender the outcome. They differ only in the flag and the success text.
 */
@DisplayName("ArenaOnCommand and ArenaOffCommand - enable and disable an arena by name")
class ArenaToggleCommandsTest {

	private static final Arena PIT = new Arena("pit", null, List.of(new ArenaPoint("w", 1, 64, 1, 0, 0)),
	                                           List.of(new ArenaPoint("w", 9, 64, 9, 0, 0)),
	                                           new ArenaPoint("w", 0, 64, 0, 0, 0), true);

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;
	private ArenaRegistry          registry;
	private ArenaMessages          text;
	private CommandSender          sender;
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

		Command.setInformationManager(mock(InformationManager.class));

		registry = mock(ArenaRegistry.class);
		text     = mock(ArenaMessages.class);
		when(text.text(anyString(), any(Object[].class))).thenAnswer(call -> {
			Object[] pairs = call.getArguments();
			return call.getArgument(0) + Arrays.toString(Arrays.copyOfRange(pairs, 1, pairs.length));
		});
		sender  = mock(CommandSender.class);
		command = new ArenaCommand(mock(JavaPlugin.class), registry, new ArenaDrafts(), text, mock(FightRules.class));
		command.initializeArguments();
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	/** Runs the operand of {@code /glw arena <verb> <name>} the way the command tree does. */
	private void run(String verb, String name) {
		for (Tree.Node<Argument> child : command.getArgument().getNode().getChildren()) {
			if (!child.getData().getArguments()[0].equals(verb)) continue;

			for (Tree.Node<Argument> operand : child.getChildren()) {
				operand.getData().executeArgument(sender, new String[]{"arena", verb, name});
			}
		}
	}

	@Test
	@DisplayName("on: an unknown arena is reported by the typed name and nothing is flipped")
	void on_unknownArena() {
		when(registry.byName("ghost")).thenReturn(Optional.empty());

		run("on", "ghost");

		verify(sender).sendMessage("Arenas.Not_Found[name, ghost]");
		verify(registry, never()).setEnabled(anyString(), anyBoolean());
		verifyNoMoreInteractions(sender);
	}

	@Test
	@DisplayName("on: a registry that cannot save answers Save_Failed and no Enabled message")
	void on_saveFails() {
		when(registry.byName("PIT")).thenReturn(Optional.of(PIT));
		when(registry.setEnabled("pit", true)).thenReturn(false);

		run("on", "PIT");

		verify(sender).sendMessage("Arenas.Save_Failed[]");
		verifyNoMoreInteractions(sender);
	}

	@Test
	@DisplayName("on: enables by the arena's own name, then reports Enabled with that name")
	void on_enables() {
		when(registry.byName("PIT")).thenReturn(Optional.of(PIT));
		when(registry.setEnabled("pit", true)).thenReturn(true);

		run("on", "PIT");

		InOrder order = inOrder(registry, sender);
		order.verify(registry).byName("PIT");
		order.verify(registry).setEnabled("pit", true);
		order.verify(sender).sendMessage("Arenas.Enabled[name, pit]");
		verifyNoMoreInteractions(sender);
		verify(registry, never()).setEnabled(anyString(), org.mockito.ArgumentMatchers.eq(false));
	}

	@Test
	@DisplayName("off: an unknown arena is reported by the typed name and nothing is flipped")
	void off_unknownArena() {
		when(registry.byName("ghost")).thenReturn(Optional.empty());

		run("off", "ghost");

		verify(sender).sendMessage("Arenas.Not_Found[name, ghost]");
		verify(registry, never()).setEnabled(anyString(), anyBoolean());
		verifyNoMoreInteractions(sender);
	}

	@Test
	@DisplayName("off: a registry that cannot save answers Save_Failed and no Disabled message")
	void off_saveFails() {
		when(registry.byName("PIT")).thenReturn(Optional.of(PIT));
		when(registry.setEnabled("pit", false)).thenReturn(false);

		run("off", "PIT");

		verify(sender).sendMessage("Arenas.Save_Failed[]");
		verifyNoMoreInteractions(sender);
	}

	@Test
	@DisplayName("off: disables by the arena's own name, then reports Disabled with that name")
	void off_disables() {
		when(registry.byName("PIT")).thenReturn(Optional.of(PIT));
		when(registry.setEnabled("pit", false)).thenReturn(true);

		run("off", "PIT");

		InOrder order = inOrder(registry, sender);
		order.verify(registry).byName("PIT");
		order.verify(registry).setEnabled("pit", false);
		order.verify(sender).sendMessage("Arenas.Disabled[name, pit]");
		verifyNoMoreInteractions(sender);
		verify(registry, never()).setEnabled(anyString(), org.mockito.ArgumentMatchers.eq(true));
	}
}
