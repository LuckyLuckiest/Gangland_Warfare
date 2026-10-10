package org.luckyraven.gangland.turf.placeholder;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.turf.task.TurfLocationTracker;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link TurfPlaceholderContribution}: the six turf- tokens inside a rival turf and outside every turf, the null answer
 * for an unknown token and for another prefix, and the nearest turf computed at most once per second.
 */
@DisplayName("TurfPlaceholderContribution - the six turf- placeholders")
class TurfPlaceholderContributionTest {

	private static final long NOW = 10_000_000_000L;

	private final FakeGangLookup      gangs   = new FakeGangLookup();
	private final FakeUserLookup      users   = new FakeUserLookup();
	private final TurfManager         turfs   = new TurfManager(new InMemoryTurfRepository());
	private final TurfLocationTracker tracker = new TurfLocationTracker(mock(JavaPlugin.class), turfs, null);
	private final World               world   = mock(World.class);
	private final AtomicLong          clock   = new AtomicLong(NOW);

	private MockedStatic<Settings>      settings;
	private TurfPlaceholderContribution placeholders;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	/** GanglandChatUtil.color reads the money symbol from Settings, so stub it the way the other module tests do. */
	@BeforeEach
	void setUp() throws IOException {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		when(world.getName()).thenReturn("world");

		gangs.register(gang(7, "Rats"));
		gangs.register(gang(2, "Blue"));

		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("14m");
		TurfMessages text = new TurfMessages(YamlFixture.fileManager(tempDir, "turf_messages",
		                                                             YamlFixture.shipped("/turf/turf_messages.yml")),
		                                     legacy, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75},
		                                                                      90, 90));
		CaptureEligibility eligibility = new CaptureEligibility(gangs, CaptureFixtures.settings(180, 15, 15, 10,
		                                                                                    new int[]{25, 50, 75}, 90,
		                                                                                    90));
		placeholders = new TurfPlaceholderContribution(tracker, turfs, gangs, users, eligibility, text, clock::get);
	}

	@AfterEach
	void releaseMoneySymbol() {
		settings.close();
	}

	private static Gang gang(int id, String name) {
		Gang gang = CaptureFixtures.gang(id);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		return gang;
	}

	private Turf docks(int x1, int z1, int x2, int z2, Integer ownerId) {
		Turf turf = new Turf(turfs.allocateId(), "Docks", new CuboidRegion("world", x1, z1, x2, z2), ownerId,
		                     BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		return turf;
	}

	/** An online player standing at (x, z) in the test world; gangId null means no gang. */
	private Player player(int x, int z, Integer gangId) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getPlayer()).thenReturn(player);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		if (gangId != null) {
			@SuppressWarnings("unchecked")
			User<Player> user = mock(User.class);
			when(user.hasGang()).thenReturn(true);
			when(user.getGangId()).thenReturn(gangId);
			users.register(player, user);
		}
		return player;
	}

	private String value(OfflinePlayer player, String token) {
		return placeholders.resolve(player, token);
	}

	@Test
	@DisplayName("inside a rival turf the six tokens name the turf, owner, state, relation and nearest turf")
	void insideRivalTurf() {
		Turf   docks  = docks(0, 0, 10, 10, 7);
		Player player = player(5, 5, 2);
		tracker.getPlayerTurfCache().put(player.getUniqueId(), docks);

		assertEquals("Docks", value(player, "turf-name"));
		assertEquals("Rats", value(player, "turf-owner"));
		assertEquals("attackable", value(player, "turf-state"));
		assertEquals("rival", value(player, "turf-relation"));
		assertEquals("Docks", value(player, "turf-nearest-name"));
		assertEquals("0", value(player, "turf-nearest-distance"));
	}

	@Test
	@DisplayName("outside every turf, with no turf in the world, each token answers a dash")
	void outsideAnswersDash() {
		Player player = player(0, 0, 2);

		for (String token : new String[]{"turf-name", "turf-owner", "turf-state", "turf-relation",
		                                 "turf-nearest-name", "turf-nearest-distance"}) {
			assertEquals("-", value(player, token), token);
		}
	}

	@Test
	@DisplayName("an unknown turf token and a token of another prefix are not answered")
	void unknownTokensAreNull() {
		Player player = player(0, 0, 2);

		assertNull(value(player, "turf-foo"));
		assertNull(value(player, "gang_name"));
	}

	@Test
	@DisplayName("the nearest turf is computed at most once per second, then refreshed")
	void nearestCachedForOneSecond() {
		docks(100, 0, 110, 10, null);
		Player player = player(0, 0, null);
		assertEquals("100", value(player, "turf-nearest-distance"));

		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 50));
		clock.set(NOW + 500);
		assertEquals("100", value(player, "turf-nearest-distance"));

		clock.set(NOW + 1000);
		assertEquals("108", value(player, "turf-nearest-distance"));
	}
}
