package org.luckyraven.gangland.turf.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.Currency;

import org.luckyraven.gangland.turf.data.Turf;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link TurfRepository#sanitizeIncomeAmount(double)} — GI-32 (P0), part 2: a non-finite {@code income_amount}
 * that somehow reached the database (pre-fix input, hand-edited row, corrupted column) must not crash every future
 * boot. Before this guard, {@code doLoadAll()} called {@code BigDecimal.valueOf(incomeAmount)} directly, and
 * {@code BigDecimal.valueOf(Double.POSITIVE_INFINITY)} throws {@link NumberFormatException} — disabling the plugin
 * on every restart until the row was manually repaired.
 */
@DisplayName("TurfRepository.sanitizeIncomeAmount — tolerates a corrupt persisted value")
class TurfRepositoryTest {

	@Test
	@DisplayName("GI-32: +Infinity on load is replaced with zero instead of crashing BigDecimal.valueOf")
	void sanitizeIncomeAmount_positiveInfinity_becomesZero() {
		assertEquals(0, Currency.ZERO.compareTo(TurfRepository.sanitizeIncomeAmount(Double.POSITIVE_INFINITY)));
	}

	@Test
	@DisplayName("GI-32: -Infinity on load is replaced with zero instead of crashing BigDecimal.valueOf")
	void sanitizeIncomeAmount_negativeInfinity_becomesZero() {
		assertEquals(0, Currency.ZERO.compareTo(TurfRepository.sanitizeIncomeAmount(Double.NEGATIVE_INFINITY)));
	}

	@Test
	@DisplayName("GI-32: NaN on load is replaced with zero instead of crashing BigDecimal.valueOf")
	void sanitizeIncomeAmount_nan_becomesZero() {
		assertEquals(0, Currency.ZERO.compareTo(TurfRepository.sanitizeIncomeAmount(Double.NaN)));
	}

	@Test
	@DisplayName("a normal finite value round-trips untouched")
	void sanitizeIncomeAmount_finiteValue_passesThrough() {
		assertEquals(0, BigDecimal.valueOf(250.5).compareTo(TurfRepository.sanitizeIncomeAmount(250.5)));
	}

	@Test
	@DisplayName("TF-14: a row whose numeric columns arrive as Long, Integer or Float (MySQL widths) maps the same way")
	void fromRow_acceptsLongAndIntegerColumns() {
		Object[] row = {
				4L, "Dock", "world", 1L, 9, -2L, 3, 7L, 250.5f, 1000, 2000L
		};

		Turf turf = TurfRepository.fromRow(row);

		assertEquals(4, turf.getId());
		assertEquals("Dock", turf.getDisplayName());
		assertEquals("world", turf.getRegion().getWorld());
		assertEquals(1, turf.getRegion().getMinX());
		assertEquals(9, turf.getRegion().getMaxX());
		assertEquals(-2, turf.getRegion().getMinZ());
		assertEquals(3, turf.getRegion().getMaxZ());
		assertEquals(Integer.valueOf(7), turf.getOwnerGangId());
		assertEquals(0, BigDecimal.valueOf(250.5).compareTo(turf.getIncomeAmount()));
		assertEquals(1000L, turf.getCreatedAt());
		assertEquals(2000L, turf.getLastCaptureTimestamp());
	}

	@Test
	@DisplayName("TF-14: a NULL owner_gang_id loads as an unclaimed turf")
	void fromRow_nullOwner_isUnclaimed() {
		Object[] row = {
				2, "Pier", "world", 0, 5, 0, 5, null, 0.0, 1L, 0L
		};

		Turf turf = TurfRepository.fromRow(row);

		assertNull(turf.getOwnerGangId());
	}
}
