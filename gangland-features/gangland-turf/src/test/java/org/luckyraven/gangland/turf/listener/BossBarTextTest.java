package org.luckyraven.gangland.turf.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.ChatColor;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link BossBarText}: the seconds-left pace formula (progress in percent, net = attackers - defenders, duration in
 * seconds) and the boss bar title filling its five placeholders.
 */
@DisplayName("BossBarText - capture boss bar time left and title")
class BossBarTextTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;

	@BeforeEach
	void stubMoneySymbol() {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	@Test
	@DisplayName("net 2 at 50 percent over 180 s has 45 s left")
	void secondsLeft_netTwo_halfway_isFortyFive() {
		assertEquals(45L, BossBarText.secondsLeft(50.0, 2, 180, 0));
	}

	@Test
	@DisplayName("net 0 is stalled")
	void secondsLeft_netZero_isStalled() {
		assertEquals(-1L, BossBarText.secondsLeft(50.0, 0, 180, 0));
	}

	@Test
	@DisplayName("net below zero is stalled")
	void secondsLeft_netNegative_isStalled() {
		assertEquals(-1L, BossBarText.secondsLeft(50.0, -1, 180, 0));
	}

	@Test
	@DisplayName("time left rounds up to whole seconds")
	void secondsLeft_roundsUp() {
		assertEquals(1L, BossBarText.secondsLeft(99.9, 2, 180, 0));
	}

	@Test
	@DisplayName("the title fills gang, turf, attackers, defenders and time, and leaves no placeholder behind")
	void title_fillsAllFivePlaceholders() throws IOException {
		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				mock(TurfMessageContract.class), capture);

		String title = BossBarText.title(text, "Rats", "Docks", 2, "1", "45s");

		assertFalse(title.contains("%"), title);
		assertTrue(title.contains("Rats") && title.contains("Docks") && title.contains("45s"), title);
	}

	@Test
	@DisplayName("an unclaimed turf is a plain tug of war: 2 attackers against 1 defender nets 1")
	void pace_unclaimedIsAPlainTugOfWar() {
		assertEquals(1, BossBarText.pace(2, 1, 0));
	}

	@Test
	@DisplayName("an unclaimed turf with one attacker against two defenders nets -1")
	void pace_unclaimedLosing_isNegative() {
		assertEquals(-1, BossBarText.pace(1, 2, 0));
	}

	@Test
	@DisplayName("guards hold back the attackers: 3 attackers, 1 defender, 2 guards nets 0")
	void pace_guardsHoldBack() {
		assertEquals(0, BossBarText.pace(3, 1, 2));
	}

	@Test
	@DisplayName("with a minimum capture time the pace is clamped: net 10 over 180 s with min 60 s gives 60 s left")
	void secondsLeft_clampedByMinCapture() {
		assertEquals(60L, BossBarText.secondsLeft(0.0, 10, 180, 60));
	}

	@Test
	@DisplayName("the minimum capture clamp counts only the remaining progress: halfway gives 30 s left")
	void secondsLeft_clampedByMinCapture_halfway() {
		assertEquals(30L, BossBarText.secondsLeft(50.0, 10, 180, 60));
	}

	@Test
	@DisplayName("no guards: the defenders label is the bare number")
	void defendersLabel_noGuards_isTheNumber() throws IOException {
		assertEquals("3", BossBarText.defendersLabel(messages(), 3, 0));
	}

	@Test
	@DisplayName("guards counted: the defenders label appends the guards suffix")
	void defendersLabel_withGuards_appendsGuards() throws IOException {
		String label = ChatColor.stripColor(BossBarText.defendersLabel(messages(), 1, 2));

		assertEquals("1 +2 guards", label);
	}

	private TurfMessages messages() throws IOException {
		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		return new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				mock(TurfMessageContract.class), capture);
	}

	@Test
	@DisplayName("the defenders of an unclaimed turf are every gang inside except the challenger")
	void unclaimedDefenders_sumsEveryOtherGang() {
		assertEquals(1, BossBarText.unclaimedDefenders(Map.of(7, 2, 9, 1), 7));
	}

	@Test
	@DisplayName("with no challenger every gang inside counts as a defender of an unclaimed turf")
	void unclaimedDefenders_noChallenger_sumsAll() {
		assertEquals(3, BossBarText.unclaimedDefenders(Map.of(7, 2, 9, 1), null));
	}
}
