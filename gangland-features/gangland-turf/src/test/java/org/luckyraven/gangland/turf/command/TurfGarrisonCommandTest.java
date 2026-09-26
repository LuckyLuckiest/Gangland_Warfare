package org.luckyraven.gangland.turf.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link TurfGarrisonCommand#parseCount(String)} — GI-40 (P3, skeptic-corrected scope: garrison message fix
 * only, {@code TurfCreateCommand}'s silent-console return left alone since 7 other turf spatial commands share that
 * exact convention). Before this, a bad {@code <count>} (non-numeric or negative) sent the generic
 * {@code ARGUMENTS_MISSING} ("Missing Arguments") even though an argument WAS supplied — just invalid — unlike the
 * sibling {@code TurfIncomeCommand}, which already uses a dedicated {@code TURF_INCOME_INVALID} for the identical
 * case.
 */
@DisplayName("TurfGarrisonCommand.parseCount — rejects invalid input the same way TurfIncomeCommand does")
class TurfGarrisonCommandTest {

	@Test
	@DisplayName("GI-40: non-numeric input is rejected")
	void parseCount_rejectsNonNumeric() {
		assertNull(TurfGarrisonCommand.parseCount("abc"));
	}

	@Test
	@DisplayName("a valid non-negative count parses correctly")
	void parseCount_acceptsNonNegative() {
		assertEquals(0, TurfGarrisonCommand.parseCount("0"));
		assertEquals(5, TurfGarrisonCommand.parseCount("5"));
	}
}
