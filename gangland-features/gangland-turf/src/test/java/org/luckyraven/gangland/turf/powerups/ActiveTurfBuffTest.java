package org.luckyraven.gangland.turf.powerups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the boundary semantics of {@link ActiveTurfBuff#isExpired(long)} and {@link ActiveTurfBuff#remainingMillis(long)}.
 * Ties to the "ActiveTurfBuff.isExpired / remainingMillis boundaries" bullet in turf.md's Test Surface section.
 */
@DisplayName("ActiveTurfBuff — isExpired and remainingMillis boundaries")
class ActiveTurfBuffTest {

	private static ActiveTurfBuff buff(long expiresAt) {
		return new ActiveTurfBuff(1L, 10, "small_income_boost", EffectType.INCOME_MULTIPLIER, 1.25, expiresAt);
	}

	@Test
	@DisplayName("isExpired is false strictly before the expiry instant")
	void isExpired_falseBeforeExpiry() {
		assertFalse(buff(1_000L).isExpired(999L));
	}

	@Test
	@DisplayName("isExpired is true exactly at the expiry instant (>=, not >)")
	void isExpired_trueExactlyAtExpiry() {
		assertTrue(buff(1_000L).isExpired(1_000L));
	}

	@Test
	@DisplayName("isExpired is true after the expiry instant")
	void isExpired_trueAfterExpiry() {
		assertTrue(buff(1_000L).isExpired(1_001L));
	}

	@Test
	@DisplayName("remainingMillis returns the exact gap before expiry")
	void remainingMillis_returnsExactGapBeforeExpiry() {
		assertEquals(500L, buff(1_000L).remainingMillis(500L));
	}

	@Test
	@DisplayName("remainingMillis clamps to 0 at the expiry instant")
	void remainingMillis_clampsToZeroAtExpiry() {
		assertEquals(0L, buff(1_000L).remainingMillis(1_000L));
	}

	@Test
	@DisplayName("remainingMillis clamps to 0 (never negative) once past expiry")
	void remainingMillis_clampsToZeroPastExpiry() {
		assertEquals(0L, buff(1_000L).remainingMillis(5_000L));
	}
}
