package org.luckyraven.gangland.turf.map;

import org.bukkit.Bukkit;
import org.bukkit.Server;
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
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link MapCommand}'s tree: the four sub-commands and their permission nodes. Set up like the gang module's
 * GangCommandsJsonParityTest, so the static Bukkit and Messages state is stubbed the same way.
 */
@DisplayName("MapCommand - /glw map sub-command tree")
class MapCommandTest {

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
		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
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

	private static MapCommand registeredMapCommand() {
		MapCommand command = new MapCommand(mock(JavaPlugin.class), new MapPrefsService(), mock(TurfMessages.class),
		                                    mock(TurfMapService.class), mock(TurfManager.class), mock(TurfSettings.class));
		command.initializeArguments();
		return command;
	}

	private static List<Argument> children(Argument parent) {
		List<Argument> result = new ArrayList<>();
		for (var child : parent.getNode().getChildren()) {
			result.add(child.getData());
		}
		return result;
	}

	@Test
	@DisplayName("the map root has exactly the auto, border, hud, legend, text and zoom sub-commands")
	void childrenLabels() {
		List<String> labels = new ArrayList<>();
		for (Argument child : children(registeredMapCommand().getArgument())) {
			labels.add(child.getArguments()[0]);
		}

		// The tree does not promise an order, so compare sorted labels.
		labels.sort(null);
		assertEquals(List.of("auto", "border", "hud", "legend", "text", "zoom"), labels);
	}

	@Test
	@DisplayName("the root permission is gangland.command.map and each child's is gangland.command.map.<label>")
	void permissionNodes() {
		MapCommand command = registeredMapCommand();

		assertEquals("gangland.command.map", command.getPermission());
		for (Argument child : children(command.getArgument())) {
			String label = child.getArguments()[0];
			assertEquals("gangland.command.map." + label, child.getPermission());
		}
	}
}
