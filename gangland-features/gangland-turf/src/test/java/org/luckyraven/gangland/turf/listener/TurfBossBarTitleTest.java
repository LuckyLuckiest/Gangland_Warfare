package org.luckyraven.gangland.turf.listener;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link TurfBossBarListener#bossTitle}: one bar for owned and unclaimed captures; the defenders label carries the
 * guards that hold the bar ("1 +2 guards") and an unclaimed turf counts every other gang as a defender.
 */
@DisplayName("TurfBossBarListener - one title per contest, guards in the defenders count")
class TurfBossBarTitleTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings>  staticSettings;
	private TurfMessageContract     messages;
	private TurfBossBarListener     listener;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");

		messages = mock(TurfMessageContract.class);
		when(messages.formatDuration(anyLong())).thenReturn("1m");

		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfMessages    text    = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				messages, capture);
		CaptureEligibility eligibility = mock(CaptureEligibility.class);
		when(eligibility.rules()).thenReturn(CaptureRules.DEFAULTS);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			listener = new TurfBossBarListener(mock(JavaPlugin.class), mock(TurfManager.class),
			                                   mock(GangLookupContract.class), mock(UserLookupContract.class),
			                                   messages, mock(TurfSoundContract.class), capture, eligibility, text);
		}
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	private static TurfRuntimeState contest(int challenger, Map<Integer, Integer> inside, int defenders, int guards) {
		TurfRuntimeState state = new TurfRuntimeState(1);
		state.setState(TurfState.CONTESTING);
		state.setPhase(CapturePhase.CLAIM);
		state.setChallengerGangId(challenger);
		state.setChallengersInside(inside);
		state.setDefendersInside(defenders);
		state.setGuardsCounted(guards);
		return state;
	}

	private static Turf turf(Integer ownerId) {
		return new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), ownerId, BigDecimal.ONE, 0L, 0L);
	}

	@Test
	@DisplayName("owned contest: the defenders label shows the guards holding the bar")
	void ownedContest_titleCarriesGuards() {
		String title = ChatColor.stripColor(listener.bossTitle(turf(1), contest(2, Map.of(2, 3), 1, 2)));

		assertTrue(title.contains("3 vs 1 +2 guards"), title);
	}

	@Test
	@DisplayName("unclaimed contest: every other gang inside is a defender, and no guards are shown without guards")
	void unclaimedContest_titleCountsEveryOtherGang() {
		String title = ChatColor.stripColor(listener.bossTitle(turf(null), contest(2, Map.of(2, 2, 3, 1), 0, 0)));

		assertTrue(title.contains("2 vs 1"), title);
		assertFalse(title.contains("guards"), title);
	}

	@Test
	@DisplayName("the title leaves no placeholder behind")
	void title_hasNoPlaceholders() {
		String title = listener.bossTitle(turf(null), contest(2, Map.of(2, 2), 0, 0));

		assertFalse(title.contains("%"), title);
	}
}
