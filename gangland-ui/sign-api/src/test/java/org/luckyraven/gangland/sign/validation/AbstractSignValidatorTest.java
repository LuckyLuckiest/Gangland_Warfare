package org.luckyraven.gangland.sign.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@link AbstractSignValidator#validate(String[])}'s 4-line matrix (Test Surface,
 * lootchests-signs-waypoints.md: "AbstractSignValidator matrix: 3-line arrays, empty/negative/
 * over-max/over-8-char price and amount, bracketed vs typed vs generated line 1").
 *
 * <p>Test lives in the same package as {@code AbstractSignValidator} so the protected
 * {@code validatePrice}/{@code validateAmount}/{@code validateSignType} helpers can be exercised
 * directly for cases that would otherwise need contrived full-line arrays.
 */
@DisplayName("AbstractSignValidator - 4-line validation matrix")
class AbstractSignValidatorTest {

	private static final SignType TYPE = new SignType("glw-buy", "buy");

	@Test
	@DisplayName("null or short line arrays are rejected before touching any line")
	void validate_tooFewLines_throws() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validate(null));
		assertThrows(SignValidationException.class, () -> validator.validate(new String[]{"glw-buy", "x", "1"}));
	}

	@Test
	@DisplayName("line 1 matches either the typed or the generated name, case-insensitively")
	void validate_signTypeMatchesTypedOrGenerated() {
		TestSignValidator validator = validator();

		assertDoesNotThrow(() -> validator.validate(lines("glw-buy", "stone", "1.0", "1")));
		assertDoesNotThrow(() -> validator.validate(lines("BUY", "stone", "1.0", "1")));
	}

	@Test
	@DisplayName("line 1 matching neither name throws")
	void validate_signTypeMismatch_throws() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validate(lines("glw-sell", "stone", "1.0", "1")));
	}

	@Test
	@DisplayName("Observation #21 (lootchests-signs-waypoints.md): validateSignType strips colors but not "
	             + "brackets, so an already-formatted [BUY] line fails re-validation even though it matches the "
	             + "generated name")
	void validate_bracketedLine_doesNotMatch_pinsObservation21() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validate(lines("[BUY]", "stone", "1.0", "1")));
	}

	@Test
	@DisplayName("empty content line throws before isValidContent is consulted")
	void validate_emptyContent_throws() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validate(lines("glw-buy", "  ", "1.0", "1")));
	}

	@Test
	@DisplayName("content rejected by the subclass hook throws")
	void validate_invalidContent_throws() {
		TestSignValidator validator = validator(Set.of("bogus"));

		assertThrows(SignValidationException.class, () -> validator.validate(lines("glw-buy", "bogus", "1.0", "1")));
	}

	@Test
	@DisplayName("price: empty, negative, non-numeric and over-max are all rejected")
	void validatePrice_rejectsBadValues() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validatePrice("   ", 2, "$"));
		assertThrows(SignValidationException.class, () -> validator.validatePrice("-1", 2, "$"));
		assertThrows(SignValidationException.class, () -> validator.validatePrice("abc", 2, "$"));
		assertThrows(SignValidationException.class, () -> validator.validatePrice("100000000.00", 2, "$"));
	}

	@Test
	@DisplayName("price text longer than 8 characters is rejected even when the numeric value is within range")
	void validatePrice_tooLongText_rejected() {
		TestSignValidator validator = validator();

		// "1234567.1" -> 9 chars, numeric value 1234567.1 is well under the 99999999.99 cap.
		assertThrows(SignValidationException.class, () -> validator.validatePrice("1234567.1", 2, "$"));
	}

	@Test
	@DisplayName("price strips the money symbol and thousands separators before parsing")
	void validatePrice_stripsMoneySymbolAndCommas() {
		TestSignValidator validator = validator();

		assertDoesNotThrow(() -> validator.validatePrice("$1,234.56", 2, "$"));
	}

	@Test
	@DisplayName("a price of exactly zero is accepted (validator has no lower bound above zero)")
	void validatePrice_zeroAccepted() {
		TestSignValidator validator = validator();

		assertDoesNotThrow(() -> validator.validatePrice("0", 2, "$"));
	}

	@Test
	@DisplayName("amount: empty, zero, negative, non-integer and over-max are all rejected")
	void validateAmount_rejectsBadValues() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validateAmount("   ", 3));
		assertThrows(SignValidationException.class, () -> validator.validateAmount("0", 3));
		assertThrows(SignValidationException.class, () -> validator.validateAmount("-1", 3));
		assertThrows(SignValidationException.class, () -> validator.validateAmount("1.5", 3));
		assertThrows(SignValidationException.class, () -> validator.validateAmount("100000000", 3));
	}

	@Test
	@DisplayName("amount text longer than 8 characters is rejected")
	void validateAmount_tooLongText_rejected() {
		TestSignValidator validator = validator();

		assertThrows(SignValidationException.class, () -> validator.validateAmount("123456789", 3));
	}

	@Test
	@DisplayName("amount strips thousands separators before parsing")
	void validateAmount_stripsCommas() {
		TestSignValidator validator = validator();

		assertDoesNotThrow(() -> validator.validateAmount("1,234", 3));
	}

	@Test
	@DisplayName("performCustomValidation runs only after every base check passes, and its exception propagates")
	void validate_customValidationHook_runsLastAndCanFail() {
		TestSignValidator validator = validator();
		validator.customValidationThrows = true;

		SignValidationException exception = assertThrows(SignValidationException.class,
		                                                  () -> validator.validate(lines("glw-buy", "stone", "1.0", "1")));

		assertTrue(validator.customValidationCalled);
		assertEquals("custom validation failed", exception.getMessage());
	}

	@Test
	@DisplayName("a fully valid 4-line sign passes without throwing")
	void validate_wellFormedSign_passes() {
		TestSignValidator validator = validator();

		assertDoesNotThrow(() -> validator.validate(lines("glw-buy", "stone", "10.50", "64")));
		assertTrue(validator.customValidationCalled);
	}

	private static String[] lines(String... values) {
		return values;
	}

	private static TestSignValidator validator() {
		return validator(Set.of());
	}

	private static TestSignValidator validator(Set<String> invalidContents) {
		return new TestSignValidator(TYPE, "$", invalidContents);
	}

	private static final class TestSignValidator extends AbstractSignValidator {

		private final Set<String> invalidContents;
		boolean customValidationCalled = false;
		boolean customValidationThrows = false;

		TestSignValidator(SignType signType, String moneySymbol, Set<String> invalidContents) {
			super(signType, moneySymbol);
			this.invalidContents = invalidContents;
		}

		@Override
		protected boolean isValidContent(String content) {
			return !invalidContents.contains(content);
		}

		@Override
		protected void performCustomValidation(String[] lines) throws SignValidationException {
			customValidationCalled = true;
			if (customValidationThrows) {
				throw new SignValidationException("custom validation failed");
			}
		}

	}

}
