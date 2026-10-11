package org.luckyraven.gangland.turf.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("MapBorderCommand - /glw map border")
class MapBorderCommandTest {

	private TurfManager       turfs;
	private TurfMessages      text;
	private Player            player;
	private World             world;
	private final List<Turf>  shown = new ArrayList<>();
	private final List<String> said = new ArrayList<>();
	private MapBorderCommand  command;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settingsStatic;
	private MockedStatic<Bukkit>   bukkit;

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
		settingsStatic = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settingsStatic.when(Settings::getMoneySymbol).thenReturn("$");
		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		Command.setInformationManager(mock(InformationManager.class));

		JavaPlugin plugin = mock(JavaPlugin.class);
		turfs  = mock(TurfManager.class);
		player = mock(Player.class);
		world  = mock(World.class);
		TurfSettings settings = mock(TurfSettings.class);
		when(settings.getVisualizationParticle()).thenReturn("FLAME");
		when(world.getName()).thenReturn("w");
		text = new TurfMessages(YamlFixture.fileManager(tempDir, "turf_messages",
		                                                YamlFixture.shipped("/turf/turf_messages.yml")),
		                        mock(TurfMessageContract.class),
		                        CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
		doAnswer(inv -> said.add(inv.getArgument(0))).when(player).sendMessage(anyString());
		MapCommand map = new MapCommand(plugin, new MapPrefsService(), text, mock(TurfMapService.class), turfs,
		                                settings);
		map.initializeArguments();
		command = new MapBorderCommand(plugin, map.getArgumentTree(), map.getArgument(), turfs, settings, text,
		                               (pl, turf, seconds, particle) -> shown.add(turf));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settingsStatic.close();
		Command.setInformationManager(null);
	}

	private Turf turf(String name, int x1, int z1, int x2, int z2) {
		Turf turf = mock(Turf.class);
		when(turf.getDisplayName()).thenReturn(name);
		when(turf.getRegion()).thenReturn(new CuboidRegion("w", x1, z1, x2, z2));
		return turf;
	}

	private void standAt(double x, double z) {
		Location location = new Location(world, x, 64, z);
		when(player.getLocation()).thenReturn(location);
		when(player.getWorld()).thenReturn(world);
	}

	@Test
	@DisplayName("standing inside a turf shows that turf")
	void insideTurf_showsIt() {
		Turf alpha = turf("Alpha", 0, 0, 100, 100);
		standAt(50, 50);
		when(turfs.findAt(any())).thenReturn(alpha);
		command.run(player);
		assertEquals(List.of(alpha), shown);
		assertTrue(said.get(0).contains("Alpha") && said.get(0).contains("10"), said.toString());
	}

	@Test
	@DisplayName("no turf within 64 blocks says so")
	void noTurfNear_saysNone() {
		standAt(500, 500);
		when(turfs.findAt(any())).thenReturn(null);
		Turf far = turf("Far", 0, 0, 100, 100);
		when(turfs.getTurfsInWorld("w")).thenReturn(List.of(far));
		command.run(player);
		assertTrue(shown.isEmpty());
		assertTrue(said.get(0).contains("64"), said.toString());
	}

	@Test
	@DisplayName("the nearest turf within 64 blocks is chosen")
	void nearestWithin64_chosen() {
		Turf near = turf("Near", 110, 0, 200, 100);
		Turf farther = turf("Farther", 140, 0, 200, 100);
		standAt(100, 50);
		when(turfs.findAt(any())).thenReturn(null);
		when(turfs.getTurfsInWorld("w")).thenReturn(List.of(farther, near));
		command.run(player);
		assertEquals(List.of(near), shown);
	}
}
