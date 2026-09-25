package org.luckyraven.gangland.item.fuel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins gi=56 (P3, half 2): {@code render}'s {@code maxFuel <= 0} branch returned "Unlimited" for a zero-capacity
 * item, but every write path ({@code Fuel.setMaxFuel}, {@code Car}/{@code VehicleSession}/{@code Jetpack}/
 * {@code Wearable}) clamps {@code currentFuel <= maxFuel}, so {@code maxFuel <= 0} always implies
 * {@code currentFuel <= 0} too — the very next {@code currentFuel <= 0} branch already renders "Empty" correctly;
 * the "Unlimited" branch was simply backwards, dead-but-reachable logic, never a real "no cap" feature.
 */
@DisplayName("FuelBar.render — zero capacity renders Empty, not Unlimited (gi=56)")
class FuelBarTest {

	@Test
	@DisplayName("zero max fuel renders Empty")
	void zeroMaxFuel_rendersEmpty() {
		assertTrue(FuelBar.render(0, 0).contains("Empty"));
	}

	@Test
	@DisplayName("a negative max fuel also renders Empty")
	void negativeMaxFuel_rendersEmpty() {
		assertTrue(FuelBar.render(0, -1).contains("Empty"));
	}
}
