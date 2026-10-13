package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HoldUpGeometry - range and aim cone")
class HoldUpGeometryTest {

	private static final Vector EYE  = new Vector(0, 0, 0);
	private static final Vector EAST = new Vector(1, 0, 0);

	private static Vector at(double distance, double offDegrees) {
		double r = Math.toRadians(offDegrees);
		return new Vector(Math.cos(r) * distance, 0, Math.sin(r) * distance);
	}

	@Test
	@DisplayName("a trader dead ahead inside the range is aimed")
	void straightAhead() {
		assertTrue(HoldUpGeometry.isAimed(EAST, EYE, at(4, 0), 6.0, 10.0));
	}

	@Test
	@DisplayName("9 degrees off counts, 11 degrees off does not (Aim_Angle 10)")
	void angleBoundary() {
		assertTrue(HoldUpGeometry.isAimed(EAST, EYE, at(4, 9), 6.0, 10.0));
		assertFalse(HoldUpGeometry.isAimed(EAST, EYE, at(4, 11), 6.0, 10.0));
	}

	@Test
	@DisplayName("beyond Range is not aimed even when dead ahead")
	void beyondRange() {
		assertTrue(HoldUpGeometry.isAimed(EAST, EYE, at(5.9, 0), 6.0, 10.0));
		assertFalse(HoldUpGeometry.isAimed(EAST, EYE, at(6.1, 0), 6.0, 10.0));
	}

	@Test
	@DisplayName("a trader behind the player is not aimed")
	void behind() {
		assertFalse(HoldUpGeometry.isAimed(EAST, EYE, at(3, 180), 6.0, 10.0));
	}

	@Test
	@DisplayName("a trader at the same point (zero vector) is not aimed and does not divide by zero")
	void samePoint() {
		assertFalse(HoldUpGeometry.isAimed(EAST, EYE, new Vector(0, 0, 0), 6.0, 10.0));
	}

	@Test
	@DisplayName("vertical offset counts toward the angle")
	void verticalOffset() {
		assertFalse(HoldUpGeometry.isAimed(EAST, EYE, new Vector(4, 4, 0), 6.0, 10.0));
	}

}
