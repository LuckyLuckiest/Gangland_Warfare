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
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.map.MapPrefsService.HudMode;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.command.argument.Argument;
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
 * {@link MapHudCommand}: the mode words that tab-complete, each word storing its mode and answering with its line, a
 * bare {@code hud} answering the current line, and an unknown word leaving the mode alone. Set up like
 * {@link MapCommandTest}, so the static Bukkit and Messages state is stubbed the same way.
 */
@DisplayName("MapHudCommand - /glw map hud on|off|default")
class MapHudCommandTest {

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MapPrefsService prefs;
	private TurfMessages    text;
	private MapHudCommand   hud;
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

		MapCommand map = new MapCommand(plugin, prefs, text);
		map.initializeArguments();
		hud = new MapHudCommand(plugin, map.getArgumentTree(), map.getArgument(), prefs, text);

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
	@DisplayName("the three mode words tab-complete in this order")
	void completionWords() {
		assertEquals(List.of("on", "off", "default"), MapHudCommand.MODE_WORDS);
	}

	@Test
	@DisplayName("each word stores its mode and answers with that mode's line")
	void eachWordSetsAndReplies() {
		hud.apply(player, "on");
		assertEquals(HudMode.ON, prefs.hudMode(id));
		verify(player).sendMessage(text.text("Hud.Mode_On", ""));

		hud.apply(player, "off");
		assertEquals(HudMode.OFF, prefs.hudMode(id));
		verify(player).sendMessage(text.text("Hud.Mode_Off", ""));

		hud.apply(player, "default");
		assertEquals(HudMode.DEFAULT, prefs.hudMode(id));
		verify(player).sendMessage(text.text("Hud.Mode_Default", ""));
	}

	@Test
	@DisplayName("a bare hud answers the line for the mode in effect")
	void noArgumentRepliesCurrent() {
		prefs.setHudMode(id, HudMode.ON);

		hud.action().accept(null, player, new String[]{"map", "hud"});

		verify(player).sendMessage(text.text("Hud.Mode_On", ""));
	}

	@Test
	@DisplayName("an unknown word leaves the mode alone and answers the current line")
	void unknownWordKeepsMode() {
		hud.apply(player, "maybe");

		assertEquals(HudMode.DEFAULT, prefs.hudMode(id));
		verify(player).sendMessage(text.text("Hud.Mode_Default", ""));
	}
}
