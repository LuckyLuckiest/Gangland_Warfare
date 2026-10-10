package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The 0.17.0 per-attacker verdict: {@link CaptureEligibility#evaluateFor} adds OVER_CAP on top of the window rules, and
 * the three new reason sentences ({@link TurfMessages#reason}) read from the shipped turf_messages.yml.
 */
@DisplayName("CaptureEligibility - OVER_CAP verdict and the new reason sentences")
class CaptureEligibilityReasonsTest {

	private static final long NOW = 100_000_000L;

	private MockedStatic<Settings> settings;

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

	private final Map<Integer, Integer> owned = new HashMap<>();

	private CaptureEligibility eligibility() {
		FakeGangLookup gangs = new FakeGangLookup();
		return new CaptureEligibility(gangs, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90),
		                              () -> CaptureRules.LEGACY, id -> 0L, id -> 3,
		                              id -> owned.getOrDefault(id, 0));
	}

	private static Turf unclaimed(long lastCapture) {
		return new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.TEN, 0L, lastCapture);
	}

	@Test
	@DisplayName("evaluateFor: an attacker already at the cap gets OVER_CAP with no until")
	void evaluateFor_overCap() {
		owned.put(2, 3);

		CaptureEligibility.Verdict verdict = eligibility().evaluateFor(unclaimed(0L), 2, NOW);

		assertEquals(Reason.OVER_CAP, verdict.reason());
		assertEquals(0L, verdict.untilMillis());
	}

	@Test
	@DisplayName("evaluateFor: an attacker below the cap is attackable")
	void evaluateFor_belowCap_isAttackable() {
		owned.put(2, 2);

		assertTrue(eligibility().evaluateFor(unclaimed(0L), 2, NOW).attackable());
	}

	@Test
	@DisplayName("evaluateFor: the cooldown wins over the cap (evaluate runs first)")
	void evaluateFor_cooldownWinsOverCap() {
		owned.put(2, 3);

		CaptureEligibility.Verdict verdict = eligibility().evaluateFor(unclaimed(NOW - 60_000L), 2, NOW);

		assertEquals(Reason.COOLDOWN, verdict.reason());
	}

	@Test
	@DisplayName("overCap is true at the cap and false below it, whatever the turf")
	void overCap_isIndependentOfTheTurf() {
		CaptureEligibility eligibility = eligibility();

		owned.put(2, 3);
		assertTrue(eligibility.overCap(2));

		owned.put(2, 2);
		assertFalse(eligibility.overCap(2));
	}

	@Test
	@DisplayName("NO_OWNERS_ONLINE names the owning gang")
	void reason_noOwnersOnline() throws IOException {
		String text = messages().reason(new CaptureEligibility.Verdict(Reason.NO_OWNERS_ONLINE, 0L), "&cRats", NOW);

		assertTrue(text.contains("Rats online"), text);
	}

	@Test
	@DisplayName("WAKING_UP names the gang and the time left in the wake-up shield")
	void reason_wakingUp() throws IOException {
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(100L)).thenReturn("1m 40s");
		String text = messages(legacy).reason(new CaptureEligibility.Verdict(Reason.WAKING_UP, NOW + 100_000L),
		                                      "Rats", NOW);

		assertTrue(text.contains("Rats"), text);
		assertTrue(text.contains("1m 40s"), text);
	}

	@Test
	@DisplayName("OVER_CAP equals the shipped Reason.Over_Cap line, coloured")
	void reason_overCap() throws IOException {
		String text = messages().reason(new CaptureEligibility.Verdict(Reason.OVER_CAP, 0L), "", NOW);

		assertEquals(GanglandChatUtil.color("&eshielded: your gang already holds its turf cap"), text);
	}

	private TurfMessages messages() throws IOException {
		return messages(mock(TurfMessageContract.class));
	}

	private TurfMessages messages(TurfMessageContract legacy) throws IOException {
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		return new TurfMessages(fileManager, legacy,
		                        CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
	}

}
