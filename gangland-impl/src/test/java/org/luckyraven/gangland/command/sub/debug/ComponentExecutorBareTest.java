package org.luckyraven.gangland.command.sub.debug;

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
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A bare {@code /glw option} and {@code /glw option click} answer with the Missing Arguments usage. */
@DisplayName("/glw option - bare nodes")
class ComponentExecutorBareTest {

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

	private static ComponentExecutorCommand command() {
		ComponentExecutorCommand command = new ComponentExecutorCommand(mock(JavaPlugin.class),
		                                                                mock(DependencyContainer.class));
		command.initializeArguments();
		return command;
	}

	@Test
	@DisplayName("bare option asks for the action")
	void bareRoot() {
		CommandSender sender = mock(CommandSender.class);

		command().onExecute(null, sender, new String[]{"option"});

		verify(sender).sendMessage(missing("/glw option <action>"));
	}

	@Test
	@DisplayName("bare option click asks for the action")
	void bareClick() {
		ComponentExecutorCommand command = command();
		CommandSender            sender  = mock(CommandSender.class);

		command.getArgument().getNode().getChildren().get(0).getData()
		       .executeArgument(sender, new String[]{"option", "click"});

		verify(sender).sendMessage(missing("/glw option click <action>"));
	}
}
