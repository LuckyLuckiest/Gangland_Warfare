package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * The verdict of {@link CaptureEligibility}: the same ten rows as the characterization, now asserting the reason and
 * the time it stops. Cooldown C = 900 000 ms, post-logoff grace P = 600 000 ms, now = 100 000 000.
 */
@DisplayName("CaptureEligibility - reason and until for each characterized row")
class CaptureEligibilityTest {

	private static final long NOW = 100_000_000L;
	private static final long C   = 900_000L;
	private static final long P   = 600_000L;

	static Stream<Arguments> rows() {
		return Stream.of(
				Arguments.of("inside cooldown", null, false, 0L, NOW - 60_000L, Reason.COOLDOWN, NOW - 60_000L + C),
				Arguments.of("cooldown exact end", null, false, 0L, NOW - C, Reason.ATTACKABLE, 0L),
				Arguments.of("one ms before cooldown end", null, false, 0L, NOW - C + 1, Reason.COOLDOWN,
				             NOW - C + 1 + C),
				Arguments.of("never captured, unclaimed", null, false, 0L, 0L, Reason.ATTACKABLE, 0L),
				Arguments.of("owner gang missing", 7, false, 0L, 0L, Reason.ATTACKABLE, 0L),
				Arguments.of("owners online now", 7, true, NOW, 0L, Reason.OWNERS_ACTIVE, NOW + P),
				Arguments.of("grace exact", 7, true, NOW - P, 0L, Reason.OWNERS_ACTIVE, NOW),
				Arguments.of("grace plus one ms", 7, true, NOW - P - 1, 0L, Reason.ATTACKABLE, 0L),
				Arguments.of("owner never seen", 7, true, 0L, 0L, Reason.ATTACKABLE, 0L),
				Arguments.of("cooldown beats owners offline", 7, true, 0L, NOW - 60_000L, Reason.COOLDOWN,
				             NOW - 60_000L + C)
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rows")
	@DisplayName("evaluate returns the characterized reason and until")
	void evaluate_matchesCharacterizedVerdict(String description, Integer ownerGangId, boolean ownerExists,
	                                          long lastOnline, long lastCapture, Reason reason, long until) {
		FakeGangLookup gangs = new FakeGangLookup();
		if (ownerExists) {
			var owner = CaptureFixtures.gang(ownerGangId);
			when(owner.getLastMemberOnlineAt()).thenReturn(lastOnline);
			gangs.register(owner);
		}
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN, 0L,
		                     lastCapture);

		CaptureEligibility.Verdict verdict = new CaptureEligibility(gangs, settings).evaluate(turf, NOW);

		assertEquals(reason, verdict.reason(), description);
		assertEquals(until, verdict.untilMillis(), description);
	}
}
