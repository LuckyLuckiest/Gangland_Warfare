package org.luckyraven.gangland.command.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ParsedAmount}, the single parse point for money amounts typed into chat commands.
 *
 * <p>Regression net for US-03 and WB-02 (users-levels-economy-bank.md #3, wanted-bounty-combat.md #2).
 * {@code Currency.parse} rejects only non-numbers, so {@code -500} used to flow straight into the balance
 * arithmetic of {@code /glw bank deposit}, {@code /glw bank withdraw} and {@code /glw bounty set}, each of
 * which then moved money the wrong way. Everything the commands need to reject lives here, so the sign
 * check cannot be forgotten at a new call site.
 */
@DisplayName("ParsedAmount — positive-amount parsing for money commands")
class ParsedAmountTest {

	@ParameterizedTest(name = "\"{0}\" parses as a valid positive amount")
	@ValueSource(strings = {"100", "1", "10.55", "0.01", "999999"})
	@DisplayName("a positive number parses and carries its value")
	void of_positiveNumber_isValid(String raw) {
		ParsedAmount parsed = ParsedAmount.of(raw);

		assertTrue(parsed.isValid());
		assertNull(parsed.failure());
		assertEquals(0, parsed.require().compareTo(new BigDecimal(raw)));
	}

	@ParameterizedTest(name = "\"{0}\" is rejected as NOT_POSITIVE")
	@ValueSource(strings = {"0", "0.00", "-1", "-50", "-0.01", "-999999"})
	@DisplayName("US-03 / WB-02: zero and negative amounts are rejected, not passed through as money")
	void of_zeroOrNegative_isNotPositive(String raw) {
		ParsedAmount parsed = ParsedAmount.of(raw);

		assertFalse(parsed.isValid());
		assertEquals(ParsedAmount.Failure.NOT_POSITIVE, parsed.failure());
		assertNull(parsed.value());
	}

	@ParameterizedTest(name = "\"{0}\" is rejected as NOT_A_NUMBER")
	@ValueSource(strings = {"abc", "", "   ", "1,000", "12x", "--5", "1.2.3"})
	@DisplayName("text that is not a number is reported separately from a non-positive number")
	void of_notANumber_isRejected(String raw) {
		ParsedAmount parsed = ParsedAmount.of(raw);

		assertFalse(parsed.isValid());
		assertEquals(ParsedAmount.Failure.NOT_A_NUMBER, parsed.failure());
		assertNull(parsed.value());
	}

	@Test
	@DisplayName("a null argument is treated as not-a-number rather than throwing")
	void of_null_isNotANumber() {
		ParsedAmount parsed = ParsedAmount.of(null);

		assertFalse(parsed.isValid());
		assertEquals(ParsedAmount.Failure.NOT_A_NUMBER, parsed.failure());
	}

	@Test
	@DisplayName("require() throws rather than handing a caller a null amount")
	void require_onFailure_throws() {
		ParsedAmount parsed = ParsedAmount.of("-5");

		assertThrows(IllegalStateException.class, parsed::require);
	}

	@Test
	@DisplayName("failureMessage() throws on a valid result — there is nothing to report")
	void failureMessage_onValid_throws() {
		ParsedAmount parsed = ParsedAmount.of("10");

		assertThrows(IllegalStateException.class, () -> parsed.failureMessage("10"));
	}

}
