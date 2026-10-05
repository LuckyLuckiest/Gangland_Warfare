package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the rope constraint behind the grapple swing: a taut rope removes only the outward radial velocity (the
 * tangential part survives, so the player swings like a pendulum), pulls position drift back onto the rope, leaves a
 * slack rope alone, and never hands out more than the speed cap.
 */
@DisplayName("GrappleRope — pendulum rope constraint and reel")
class GrappleRopeTest {

	private static final Vector ANCHOR = new Vector(0, 0, 0);

	private static void assertVector(double x, double y, double z, Vector actual) {
		assertEquals(x, actual.getX(), 1e-9, "x");
		assertEquals(y, actual.getY(), 1e-9, "y");
		assertEquals(z, actual.getZ(), 1e-9, "z");
	}

	@Test
	@DisplayName("taut rope: the outward radial component is removed, the tangential component is preserved")
	void taut_removesOutwardRadial_keepsTangential() {
		// Hanging 10 blocks straight below the anchor on a 10-block rope, moving sideways and falling.
		Vector velocity = GrappleRope.constrain(new Vector(0, -10, 0), new Vector(1, -0.5, 0), ANCHOR, 10, 100);

		assertNotNull(velocity);
		assertVector(1, 0, 0, velocity);
	}

	@Test
	@DisplayName("taut rope: an inward radial component is left alone")
	void taut_keepsInwardRadial() {
		Vector velocity = GrappleRope.constrain(new Vector(0, -10, 0), new Vector(1, 0.3, 0), ANCHOR, 10, 100);

		assertNotNull(velocity);
		assertVector(1, 0.3, 0, velocity);
	}

	@Test
	@DisplayName("beyond the rope length: the excess is corrected through velocity toward the anchor")
	void beyondRope_correctsDriftInward() {
		Vector velocity = GrappleRope.constrain(new Vector(0, -11, 0), new Vector(0, 0, 0), ANCHOR, 10, 100);

		assertNotNull(velocity);
		assertVector(0, 1, 0, velocity);
	}

	@Test
	@DisplayName("inside the rope length the rope is slack: no velocity change at all")
	void slack_noForce() {
		assertNull(GrappleRope.constrain(new Vector(0, -8, 0), new Vector(1, -0.5, 0), ANCHOR, 10, 100));
	}

	@Test
	@DisplayName("the constrained velocity never exceeds the speed cap and keeps its direction")
	void speedCap() {
		Vector velocity = GrappleRope.constrain(new Vector(0, -10, 0), new Vector(5, 0, 0), ANCHOR, 10, 2);

		assertNotNull(velocity);
		assertVector(2, 0, 0, velocity);
	}

	@Test
	@DisplayName("reel shortens the rope by the reel speed, never below the minimum length")
	void reel_shortensAndFloors() {
		assertEquals(9.5, GrappleRope.reel(10, 0.5, 1.5), 1e-9);
		assertEquals(1.5, GrappleRope.reel(1.8, 0.5, 1.5), 1e-9);
	}

	@Test
	@DisplayName("reel never lengthens a rope that latched on shorter than the minimum length")
	void reel_neverLengthens() {
		assertEquals(2.0, GrappleRope.reel(2.0, 0.3, 3.0), 1e-9);
	}

	@Test
	@DisplayName("a player exactly on the anchor (zero radius) gets no rope force, never a NaN velocity")
	void zeroRadius_noNaN() {
		assertNull(GrappleRope.constrain(new Vector(0, 0, 0), new Vector(1, 0, 0), ANCHOR, 0, 2));
	}
}
