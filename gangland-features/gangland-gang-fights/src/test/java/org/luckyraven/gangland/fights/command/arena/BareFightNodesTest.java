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
import org.luckyraven.gangland.fights.command.fight.FightCommand;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A fight or arena node that needs an operand answers a bare call with the Missing Arguments usage. */
@DisplayName("Bare fight and arena nodes")
class BareFightNodesTest {

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;

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
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	private static String missing(String usage) {
		return GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), usage);
	}

	private static void bareArena(String verb, String usage) {
		ArenaCommand  command = ArenaTreeFixture.command(mock(JavaPlugin.class));
		CommandSender sender  = mock(CommandSender.class);

		for (var child : command.getArgument().getNode().getChildren()) {
			if (child.getData().getArguments()[0].equals(verb)) {
				child.getData().executeArgument(sender, new String[]{"arena", verb});
			}
		}

		verify(sender).sendMessage(missing(usage));
	}

	@Test
	@DisplayName("bare arena create asks for the name")
	void create() {
		bareArena("create", "/glw arena create <name>");
	}

	@Test
	@DisplayName("bare arena delete asks for the name")
	void delete() {
		bareArena("delete", "/glw arena delete <name>");
	}

	@Test
	@DisplayName("bare arena on asks for the name")
	void on() {
		bareArena("on", "/glw arena on <name>");
	}

	@Test
	@DisplayName("bare arena off asks for the name")
	void off() {
		bareArena("off", "/glw arena off <name>");
	}

	@Test
	@DisplayName("bare arena tp asks for the name")
	void tp() {
		bareArena("tp", "/glw arena tp <name>");
	}

	@Test
	@DisplayName("bare arena spawn asks for the side")
	void spawn() {
		bareArena("spawn", "/glw arena spawn <a|b>");
	}

	@Test
	@DisplayName("bare gang fight asks for the action instead of staying silent")
	void bareFight() {
		FightCommand  fight  = new FightCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), null);
		CommandSender sender = mock(CommandSender.class);

		fight.executeArgument(sender, new String[]{"gang", "fight"});

		verify(sender).sendMessage(missing("/glw gang fight <action>"));
	}
}
