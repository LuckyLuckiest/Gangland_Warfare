package org.luckyraven.gangland.turf.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link TurfIncomeCommand#parseAmount(String)} — GI-32 (P0): a raw {@code new BigDecimal(raw)} accepted
 * unbounded-exponent input like {@code 1e999}, which later overflows to {@code Infinity} via
 * {@code TurfTable.getData()}'s {@code doubleValue()} and then crashes every future boot in
 * {@code TurfRepository.doLoadAll()} ({@code BigDecimal.valueOf(Infinity)} throws {@link NumberFormatException}).
 * {@code parseAmount} now routes through {@code Currency.parse}, which already bounds the scale.
 */
@DisplayName("TurfIncomeCommand.parseAmount — rejects non-finite magnitudes")
class TurfIncomeCommandTest {

	@Test
	@DisplayName("GI-32: an unbounded exponent (1e999) is rejected, not silently accepted as Infinity")
	void parseAmount_rejectsUnboundedExponent() {
		assertNull(TurfIncomeCommand.parseAmount("1e999"));
	}

	@Test
	@DisplayName("GI-32: an unbounded negative exponent is rejected too")
	void parseAmount_rejectsUnboundedNegativeExponent() {
		assertNull(TurfIncomeCommand.parseAmount("1e-999"));
	}

	@Test
	@DisplayName("a plain, in-range decimal still parses correctly")
	void parseAmount_acceptsPlainDecimal() {
		assertEquals(0, new BigDecimal("500").compareTo(TurfIncomeCommand.parseAmount("500")));
	}

	@Test
	@DisplayName("garbage input is still rejected")
	void parseAmount_rejectsGarbage() {
		assertNull(TurfIncomeCommand.parseAmount("not-a-number"));
	}
}
