package org.luckyraven.gangland.turf.map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.map.MapPrefsService.HudMode;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.turf.task.TurfLocationTracker;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TurfHudTask}: one line per player per tick, never skipped when unchanged; the priority passed on; the
 * mode rules (OFF, DEFAULT outside, ON outside) and the Hud.Enabled switch. Ticks are run by calling tick() directly
 * against recording fakes, so no scheduler runs.
 */
@DisplayName("TurfHudTask - the action-bar turf line every interval")
class TurfHudTaskTest {

	private static final long NOW = 10_000_000_000L;

	private record Sent(Player player, String line, int priority) {
	}

	private final List<Sent>       sent    = new ArrayList<>();
	private final List<Player>     online  = new ArrayList<>();
	private final FakeGangLookup   gangs   = new FakeGangLookup();
	private final FakeUserLookup   users   = new FakeUserLookup();
	private final MapPrefsService  prefs   = new MapPrefsService();
	private final TurfManager      turfs   = new TurfManager(new InMemoryTurfRepository());
	private final TurfLocationTracker tracker = new TurfLocationTracker(mock(JavaPlugin.class), turfs, null);
	private final World            world   = mock(World.class);

	private MockedStatic<Settings> settings;

	/** GanglandChatUtil.color reads the money symbol from Settings, so stub it the way the other module tests do. */
	@BeforeEach
	void stubMoneySymbol() {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		when(world.getName()).thenReturn("world");
	}

	@AfterEach
	void releaseMoneySymbol() {
		settings.close();
	}

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfHudTask task(String mapYaml) throws IOException {
		TurfMapSettings map = new TurfMapSettings(YamlFixture.fileManager(tempDir, "turf_map", mapYaml));
		FileManager     messagesFiles = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                        YamlFixture.shipped("/turf/turf_messages.yml"));
		TurfMessages    text          = new TurfMessages(messagesFiles, mock(TurfMessageContract.class),
		                                                 CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75},
		                                                                          90, 90));
		CaptureEligibility eligibility = new CaptureEligibility(gangs, CaptureFixtures.settings(180, 15, 15, 10,
		                                                                                    new int[]{25, 50, 75}, 90,
		                                                                                    90));
		return new TurfHudTask(tracker, turfs, gangs, users, eligibility, text, map, prefs,
		                       (player, line, priority) -> sent.add(new Sent(player, line, priority)),
		                       () -> online, () -> NOW);
	}


	private Turf turf(int x1, int z1, int x2, int z2) {
		Turf turf = new Turf(turfs.allocateId(), "Docks", new CuboidRegion("world", x1, z1, x2, z2), null,
		                     BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	private Player player(int x, int z) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		online.add(player);
		return player;
	}

	private static String plain(String coloured) {
		return ChatColor.stripColor(coloured);
	}

	@Test
	@DisplayName("a player inside a turf gets a line on every tick, even when the text did not change")
	void insideGetsLineEveryTick() throws IOException {
		Turf docks = turf(0, 0, 10, 10);
		Player player = player(5, 5);
		tracker.getPlayerTurfCache().put(player.getUniqueId(), docks);
		TurfHudTask task = task("");

		task.tick();
		task.tick();

		assertEquals(2, sent.size());
		assertEquals(sent.get(0).line(), sent.get(1).line());
	}

	@Test
	@DisplayName("the line is sent at the configured Hud.Priority (9 by default)")
	void priorityIsNine() throws IOException {
		Turf docks = turf(0, 0, 10, 10);
		Player player = player(5, 5);
		tracker.getPlayerTurfCache().put(player.getUniqueId(), docks);
		TurfHudTask task = task("");

		task.tick();

		assertEquals(9, sent.get(0).priority());
	}

	@Test
	@DisplayName("a player who turned the turf line off gets nothing, even inside a turf")
	void offSendsNothing() throws IOException {
		Turf docks = turf(0, 0, 10, 10);
		Player player = player(5, 5);
		tracker.getPlayerTurfCache().put(player.getUniqueId(), docks);
		prefs.setHudMode(player.getUniqueId(), HudMode.OFF);

		task("").tick();

		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("outside every turf the default mode sends nothing")
	void outsideDefaultSendsNothing() throws IOException {
		turf(100, 0, 110, 10);
		player(0, 0);

		task("").tick();

		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("outside every turf with the map hud on sends the nearest turf line")
	void outsideOnSendsNearest() throws IOException {
		turf(100, 0, 110, 10);
		Player player = player(0, 0);
		prefs.setHudMode(player.getUniqueId(), HudMode.ON);

		task("").tick();

		assertEquals("Nearest turf: Docks 100m E", plain(sent.get(0).line()));
	}

	@Test
	@DisplayName("T-224: a reload cancels the running HUD task and re-arms it with the new Hud.Interval_Seconds")
	void reloadReArmsWithNewInterval() {
		TurfMapSettings map = mock(TurfMapSettings.class);
		when(map.getHudIntervalSeconds()).thenReturn(2);
		JavaPlugin plugin = mock(JavaPlugin.class);
		BukkitTask first = mock(BukkitTask.class);
		BukkitTask second = mock(BukkitTask.class);
		TurfHudTask task = new TurfHudTask(tracker, turfs, gangs, users, null, null, map, prefs,
		                                   (player, line, priority) -> {
		                                   }, () -> online, () -> NOW);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			when(bukkit.scheduler().runTaskTimer(eq(plugin), any(Runnable.class), eq(40L), eq(40L)))
					.thenReturn(first);
			task.start(plugin);

			when(map.getHudIntervalSeconds()).thenReturn(5);
			when(bukkit.scheduler().runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(100L)))
					.thenReturn(second);
			task.onInitialize(false);

			verify(first).cancel();
			verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(100L));
		}
	}

	@Test
	@DisplayName("Hud.Enabled: false disables the task for everyone")
	void disabledSendsNothing() throws IOException {
		Turf docks = turf(0, 0, 10, 10);
		Player player = player(5, 5);
		tracker.getPlayerTurfCache().put(player.getUniqueId(), docks);

		task("Hud:\n   Enabled: false\n").tick();

		assertTrue(sent.isEmpty());
	}
}
