package org.luckyraven.gangland.turf.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

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
		assertEquals(45L, BossBarText.secondsLeft(50.0, 2, 180));
	}

	@Test
	@DisplayName("net 0 is stalled")
	void secondsLeft_netZero_isStalled() {
		assertEquals(-1L, BossBarText.secondsLeft(50.0, 0, 180));
	}

	@Test
	@DisplayName("net below zero is stalled")
	void secondsLeft_netNegative_isStalled() {
		assertEquals(-1L, BossBarText.secondsLeft(50.0, -1, 180));
	}

	@Test
	@DisplayName("time left rounds up to whole seconds")
	void secondsLeft_roundsUp() {
		assertEquals(1L, BossBarText.secondsLeft(99.9, 2, 180));
	}

	@Test
	@DisplayName("the title fills gang, turf, attackers, defenders and time, and leaves no placeholder behind")
	void title_fillsAllFivePlaceholders() throws IOException {
		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				mock(TurfMessageContract.class), capture);

		String title = BossBarText.title(text, "Rats", "Docks", 2, 1, "45s");

		assertFalse(title.contains("%"), title);
		assertTrue(title.contains("Rats") && title.contains("Docks") && title.contains("45s"), title);
	}
}
