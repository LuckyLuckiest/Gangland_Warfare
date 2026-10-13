package org.luckyraven.gangland.turf.map;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MapZoomCommand}: the size steps (in halves the size, out doubles it, the ends hold), an unknown size read as
 * 16, and {@code apply} storing the player's own size and answering each step. Set up like {@link MapHudCommandTest}.
 */
@DisplayName("MapZoomCommand - /glw map zoom in|out")
class MapZoomCommandTest {

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MapPrefsService prefs;
	private TurfMessages    text;
	private MapZoomCommand  zoom;
	private Player          player;
	private UUID            id;

	@BeforeEach
	void setUp() throws IOException {
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

		JavaPlugin plugin = mock(JavaPlugin.class);
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		text = new TurfMessages(YamlFixture.fileManager(tempDir, "turf_messages",
		                                                YamlFixture.shipped("/turf/turf_messages.yml")), legacy,
		                        CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
		prefs = new MapPrefsService();

		MapCommand map = new MapCommand(plugin, prefs, text, mock(TurfMapService.class), mock(TurfManager.class),
		                                mock(TurfSettings.class));
		map.initializeArguments();
		zoom = new MapZoomCommand(plugin, map.getArgumentTree(), map.getArgument(), prefs, text);

		id     = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("in halves the size: 64 to 32, 16 to 8, and 8 holds")
	void zoomIn_halvesDownTo8() {
		assertEquals(32, MapZoomCommand.zoomIn(64));
		assertEquals(8, MapZoomCommand.zoomIn(16));
		assertEquals(8, MapZoomCommand.zoomIn(8));
	}

	@Test
	@DisplayName("out doubles the size: 8 to 16, and 64 holds")
	void zoomOut_doublesUpTo64() {
		assertEquals(16, MapZoomCommand.zoomOut(8));
		assertEquals(64, MapZoomCommand.zoomOut(64));
	}

	@Test
	@DisplayName("a size that is not one of the four is read as 16")
	void unknownSize_treatedAs16() {
		assertEquals(8, MapZoomCommand.zoomIn(20));
		assertEquals(32, MapZoomCommand.zoomOut(20));
	}

	@Test
	@DisplayName("apply stores the player's size and answers each step; a limit or an unknown word stores nothing")
	void apply_storesAndAnswers() {
		zoom.apply(player, "in");
		assertEquals(8, prefs.cellBlocks(id));
		verify(player).sendMessage(text.text("Map.Zoom_Set", "", "blocks", 8));

		zoom.apply(player, "in");
		assertEquals(8, prefs.cellBlocks(id));
		verify(player).sendMessage(text.text("Map.Zoom_Limit_In", "", "blocks", 8));

		zoom.apply(player, "x");
		assertEquals(8, prefs.cellBlocks(id));
		verify(player).sendMessage(text.text("Map.Zoom_Usage", "", "blocks", 8));
	}
}
