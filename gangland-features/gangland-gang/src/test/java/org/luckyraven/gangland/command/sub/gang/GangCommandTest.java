package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Bare {@code /glw gang} only reaches {@code onExecute} when the {@code gang_info.yml} menu did not take the command
 * (no inventory permission, or no such menu); it must still answer, gang member or not.
 */
@DisplayName("GangCommand - bare /glw gang always answers")
class GangCommandTest {

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;

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

	@ParameterizedTest(name = "in a gang: {0}")
	@ValueSource(booleans = {true, false})
	@SuppressWarnings("unchecked")
	void bareGangCommand_showsHelp(boolean inGang) {
		Player              player      = mock(Player.class);
		User<Player>        user        = mock(User.class);
		UserManager<Player> userManager = mock(UserManager.class);
		when(user.hasGang()).thenReturn(inGang);
		when(userManager.getUser(player)).thenReturn(user);

		GangCommand command = new GangCommand(mock(JavaPlugin.class), userManager, mock(UserManager.class), null, null,
		                                      null, null, null, mock(DependencyContainer.class), null);

		command.onExecute(command.getArgument(), player, new String[]{"gang"});

		verify(player).sendMessage(Messages.COMMAND_HELP_EMPTY.toString());
	}

}
