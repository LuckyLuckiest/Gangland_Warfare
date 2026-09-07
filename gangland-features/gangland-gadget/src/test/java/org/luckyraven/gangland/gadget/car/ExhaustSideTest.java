package org.luckyraven.gangland.gadget.car;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link ExhaustSide}'s NBT-round-trip parsing (gadgets-cars-fuel-jetpack.md — {@code car_exhaust_side} NBT
 * tag, see the "Configuration & Data" and W3/W6 sections: exhaust side is randomly assigned once, then persisted
 * across sessions).
 */
@DisplayName("ExhaustSide — fromString round-trip and random() fallback")
class ExhaustSideTest {

	@Test
	@DisplayName("fromString parses a valid persisted enum name back exactly")
	void fromString_validName_parsesExactly() {
		assertSame(ExhaustSide.LEFT, ExhaustSide.fromString("LEFT"));
		assertSame(ExhaustSide.RIGHT, ExhaustSide.fromString("RIGHT"));
		assertSame(ExhaustSide.BOTH, ExhaustSide.fromString("BOTH"));
	}

	@Test
	@DisplayName("fromString falls back to a random value for corrupt or missing NBT text, never throws")
	void fromString_corruptOrMissingText_fallsBackToRandom() {
		Set<ExhaustSide> valid = EnumSet.allOf(ExhaustSide.class);

		assertTrue(valid.contains(ExhaustSide.fromString("left"))); // wrong case is NOT valueOf-compatible
		assertTrue(valid.contains(ExhaustSide.fromString("")));
		assertTrue(valid.contains(ExhaustSide.fromString("not-a-real-side")));
	}

	@RepeatedTest(20)
	@DisplayName("random() always returns a value from the enum's value set")
	void random_alwaysReturnsAValidConstant() {
		assertTrue(EnumSet.allOf(ExhaustSide.class).contains(ExhaustSide.random()));
	}

}
