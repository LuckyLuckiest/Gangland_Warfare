package org.luckyraven.gangland.sign.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code [WANTED]} sign: line 2 is {@code increase}, {@code remove} or {@code clear}; unless it is {@code clear},
 * line 3 is a positive star count and line 4 is an optional positive price. The checks run in that order and the first
 * failure wins. The exception text carries the line number the validator passes plus one, so the 1-based numbers it
 * passes for its own lines read one too high ({@code Line 4} for the star line): pinned as it is today.
 */
@DisplayName("WantedSignValidator - the stars and price lines")
class WantedSignValidatorTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private WantedSignValidator validator;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);

		validator = new WantedSignValidator(new SignType("[Wanted]", "glw-wanted"));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private String failure(String content, String stars, String price) {
		SignValidationException thrown = assertThrows(SignValidationException.class,
		                                              () -> validator.performCustomValidation(
				                                              new String[]{"[Wanted]", content, stars, price}));
		return thrown.getMessage();
	}

	private void passes(String content, String stars, String price) {
		assertDoesNotThrow(() -> validator.performCustomValidation(new String[]{"[Wanted]", content, stars, price}));
	}

	@Test
	@DisplayName("line 2 accepts the three operations in any case and nothing else")
	void contentLine() {
		assertTrue(validator.isValidContent("increase"));
		assertTrue(validator.isValidContent("REMOVE"));
		assertTrue(validator.isValidContent("Clear"));
		assertFalse(validator.isValidContent("bogus"));
	}

	@Test
	@DisplayName("an empty or unknown operation is refused before the stars are looked at")
	void badOperation() {
		assertEquals("Line 2 must be 'increase', 'remove', or 'clear' (Line 3: 'bogus')", failure("bogus", "", ""));
		assertEquals("Line 2 must be 'increase', 'remove', or 'clear' (Line 3: '')", failure("", "3", "10"));
	}

	@Test
	@DisplayName("clear needs no stars and no price, whatever they say")
	void clearSkipsTheNumbers() {
		passes("clear", "", "");
		passes("CLEAR", "garbage", "garbage");
	}

	@Test
	@DisplayName("a whole positive star count passes, with or without a price")
	void validStarsAndPrice() {
		passes("increase", "3", "");
		passes("remove", "1", "50");
		passes("increase", "2", "7");
	}

	@Test
	@DisplayName("an empty star line is refused")
	void emptyStars() {
		assertEquals("Line 3 must specify number of stars (Line 4: '')", failure("increase", "", ""));
	}

	@Test
	@DisplayName("a zero or negative star count is refused as not positive, keeping the line text")
	void nonPositiveStars() {
		assertEquals("Star count must be positive (Line 4: '0')", failure("increase", "0", ""));
		assertEquals("Star count must be positive (Line 4: '-2')", failure("remove", "-2", ""));
	}

	@Test
	@DisplayName("a star count that is no whole number (decimal, padded, text) is refused as not a number")
	void nonNumericStars() {
		assertEquals("Star count must be a valid number (Line 4: 'x')", failure("increase", "x", ""));
		assertEquals("Star count must be a valid number (Line 4: '2.5')", failure("increase", "2.5", ""));
		assertEquals("Star count must be a valid number (Line 4: ' 3')", failure("increase", " 3", ""));
	}

	@Test
	@DisplayName("an empty price is accepted, a zero or negative one is refused as not positive")
	void nonPositivePrice() {
		passes("increase", "3", "");
		assertEquals("Price must be positive (Line 5: '0')", failure("increase", "3", "0"));
		assertEquals("Price must be positive (Line 5: '-9')", failure("remove", "3", "-9"));
	}

	@Test
	@DisplayName("a price that is no whole number is refused as not a number")
	void nonNumericPrice() {
		assertEquals("Price must be a valid number (Line 5: 'abc')", failure("increase", "3", "abc"));
		assertEquals("Price must be a valid number (Line 5: '1.5')", failure("increase", "3", "1.5"));
	}

	@Test
	@DisplayName("the stars are checked before the price, so a bad star line wins over a bad price")
	void starsBeforePrice() {
		assertEquals("Star count must be positive (Line 4: '0')", failure("increase", "0", "abc"));
		assertEquals("Star count must be a valid number (Line 4: 'x')", failure("increase", "x", "0"));
	}

	@Test
	@DisplayName("colour codes are stripped from the star and price lines before they are read")
	void colourCodesAreStripped() {
		passes("increase", "&a3", "&c50");
		assertEquals("Star count must be positive (Line 4: '0')", failure("increase", "&a0", ""));
		assertEquals("Price must be positive (Line 5: '0')", failure("increase", "3", "&c0"));
	}

	@Test
	@DisplayName("validate runs the sign type, content and the stars and price lines end to end")
	void validateEndToEnd() {
		assertDoesNotThrow(() -> validator.validate(new String[]{"[Wanted]", "increase", "3", "10"}));
		assertDoesNotThrow(() -> validator.validate(new String[]{"[Wanted]", "clear", "", ""}));

		SignValidationException thrown = assertThrows(SignValidationException.class, () -> validator.validate(
				new String[]{"[Wanted]", "increase", "0", ""}));
		assertEquals("Star count must be positive (Line 4: '0')", thrown.getMessage());

		thrown = assertThrows(SignValidationException.class, () -> validator.validate(
				new String[]{"[Wanted]", "bogus", "3", ""}));
		assertEquals("Invalid content: bogus (Line 2: 'bogus')", thrown.getMessage());
	}
}
