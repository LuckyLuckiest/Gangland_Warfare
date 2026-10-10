package org.luckyraven.gangland.turf.map;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link TurfHudLine}: the action-bar turf line for each relation and capture state, and the nearest-turf line. The
 * texts come from the shipped turf_messages.yml; the verdicts are built by hand, so no Bukkit world is needed.
 */
@DisplayName("TurfHudLine - the action-bar turf line")
class TurfHudLineTest {

	private static final long                   NOW     = 10_000_000_000L;
	private static final CaptureEligibility.Verdict ATTACKABLE =
			new CaptureEligibility.Verdict(Reason.ATTACKABLE, 0L);

	private MockedStatic<Settings> settings;

	/** GanglandChatUtil.color reads the money symbol from Settings, so stub it the way the other module tests do. */
	@BeforeEach
	void stubMoneySymbol() {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
	}

	@AfterEach
	void releaseMoneySymbol() {
		settings.close();
	}

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfMessages text() throws IOException {
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(anyLong())).thenReturn("14m");
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		return new TurfMessages(fileManager, legacy, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75},
		                                                                      90, 90));
	}

	private static Turf docks(Integer ownerId) {
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), ownerId, BigDecimal.TEN, 0L, 0L);
	}

	private static TurfRuntimeState contesting(double progress, int challengerId) {
		TurfRuntimeState state = new TurfRuntimeState(1);
		state.setState(TurfState.CONTESTING);
		state.setCaptureProgress(progress);
		state.setChallengerGangId(challengerId);
		return state;
	}

	private static String plain(String coloured) {
		return ChatColor.stripColor(coloured);
	}

	@Test
	@DisplayName("own turf nobody attacks reads your turf - quiet")
	void ownQuiet() throws IOException {
		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.OWN, "Rats", null, null, ATTACKABLE, NOW);

		assertEquals("Docks - your turf - quiet", plain(line));
	}

	@Test
	@DisplayName("own turf under attack names the challenger and the rounded progress")
	void ownUnderAttack() throws IOException {
		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.OWN, "Rats", contesting(62.4, 9), "Vipers",
		                                 ATTACKABLE, NOW);

		assertEquals("Docks - your turf - under attack by Vipers, 62%", plain(line));
	}

	@Test
	@DisplayName("allied turf nobody attacks reads with the owner name and quiet")
	void allyQuiet() throws IOException {
		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.ALLY, "Allies", null, null, ATTACKABLE, NOW);

		assertEquals("Docks - Allies turf - quiet", plain(line));
	}

	@Test
	@DisplayName("rival turf that can be captured reads attackable")
	void rivalAttackable() throws IOException {
		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.RIVAL, "Rats", null, null, ATTACKABLE, NOW);

		assertEquals("Docks - Rats turf - attackable", plain(line));
	}

	@Test
	@DisplayName("rival turf in cooldown carries the one reason sentence with the time left")
	void rivalCooldown() throws IOException {
		CaptureEligibility.Verdict cooldown =
				new CaptureEligibility.Verdict(Reason.COOLDOWN, NOW + 14 * 60_000L);

		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.RIVAL, "Rats", null, null, cooldown, NOW);

		assertEquals("Docks - Rats turf - shielded: just captured, attackable in 14m", plain(line));
	}

	@Test
	@DisplayName("unclaimed turf reads unclaimed and the reason sentence")
	void unclaimedAttackable() throws IOException {
		String line = TurfHudLine.inside(text(), docks(null), TurfRelation.UNCLAIMED, "", null, null, ATTACKABLE, NOW);

		assertEquals("Docks - unclaimed - attackable", plain(line));
	}

	@Test
	@DisplayName("a player without a gang is told to join one to capture")
	void gangless() throws IOException {
		String line = TurfHudLine.inside(text(), docks(7), TurfRelation.GANGLESS, "Rats", null, null, ATTACKABLE, NOW);

		assertEquals("Docks - Rats turf - join a gang to capture", plain(line));
	}

	@Test
	@DisplayName("the nearest line names the turf, its distance and the compass word")
	void nearestLine() throws IOException {
		String line = TurfHudLine.nearest(text(), new TurfGeometry.Nearest(docks(7), 320, "NE"));

		assertEquals("Nearest turf: Docks 320m NE", plain(line));
	}

	@Test
	@DisplayName("no nearest turf gives no nearest line")
	void nearestNull() throws IOException {
		assertNull(TurfHudLine.nearest(text(), null));
	}
}
