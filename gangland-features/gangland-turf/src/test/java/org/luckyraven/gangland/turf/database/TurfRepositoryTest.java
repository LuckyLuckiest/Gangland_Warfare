package org.luckyraven.gangland.turf.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
