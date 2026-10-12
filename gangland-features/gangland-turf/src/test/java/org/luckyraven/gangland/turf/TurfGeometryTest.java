package org.luckyraven.gangland.turf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link TurfGeometry}: block distance, the 8-way compass and the nearest turf. North is -Z. */
@DisplayName("TurfGeometry - distance, bearing and nearest")
class TurfGeometryTest {

	/** A region from x 0..10, z 0..10 in world "world". */
	private static final CuboidRegion REGION = new CuboidRegion("world", 0, 0, 10, 10);

	private static Turf turf(int id, CuboidRegion region) {
		return new Turf(id, "T" + id, region, null, BigDecimal.ZERO, 0L, 0L);
	}

	@Test
	@DisplayName("a point inside the region is at distance 0")
	void insideIsZero() {
		assertEquals(0, TurfGeometry.distance(REGION, 5, 5));
	}

	@Test
	@DisplayName("a point 3 blocks west of the region is at distance 3")
	void straightDistance() {
		assertEquals(3, TurfGeometry.distance(REGION, -3, 5));
	}

	@Test
	@DisplayName("a diagonal offset of 3 and 4 is at distance 5")
	void diagonalDistance() {
		assertEquals(5, TurfGeometry.distance(REGION, -3, -4));
	}

	@Test
	@DisplayName("each of the eight bearings from the origin")
	void bearings() {
		assertEquals("N", TurfGeometry.bearing(0, 0, 0, -10));
		assertEquals("NE", TurfGeometry.bearing(0, 0, 10, -10));
		assertEquals("E", TurfGeometry.bearing(0, 0, 10, 0));
		assertEquals("SE", TurfGeometry.bearing(0, 0, 10, 10));
		assertEquals("S", TurfGeometry.bearing(0, 0, 0, 10));
		assertEquals("SW", TurfGeometry.bearing(0, 0, -10, 10));
		assertEquals("W", TurfGeometry.bearing(0, 0, -10, 0));
		assertEquals("NW", TurfGeometry.bearing(0, 0, -10, -10));
	}

	@Test
	@DisplayName("equal points have no bearing")
	void samePointHasNoBearing() {
		assertEquals("", TurfGeometry.bearing(4, 4, 4, 4));
	}

	@Test
	@DisplayName("the nearest of two turfs wins, with its distance and bearing")
	void nearestOfTwo() {
		Turf near = turf(2, new CuboidRegion("world", 20, 0, 30, 10));
		Turf far  = turf(1, new CuboidRegion("world", 100, 0, 110, 10));

		TurfGeometry.Nearest nearest = TurfGeometry.nearest(List.of(far, near), 25, 5);

		assertEquals(2, nearest.turf().getId());
		assertEquals(0, nearest.distance());
	}

	@Test
	@DisplayName("a turf to the east reports distance and the east bearing")
	void nearestBearing() {
		Turf east = turf(1, new CuboidRegion("world", 20, 0, 30, 10));

		TurfGeometry.Nearest nearest = TurfGeometry.nearest(List.of(east), 0, 5);

		assertEquals(20, nearest.distance());
		assertEquals("E", nearest.direction());
	}

	@Test
	@DisplayName("a tie in distance goes to the lower turf id")
	void tieGoesToLowerId() {
		Turf high = turf(7, new CuboidRegion("world", 10, 0, 20, 10)); // 5 blocks east of (5, 5)
		Turf low  = turf(3, new CuboidRegion("world", -10, 0, 0, 10)); // 5 blocks west of (5, 5)

		TurfGeometry.Nearest nearest = TurfGeometry.nearest(List.of(high, low), 5, 5);

		assertEquals(3, nearest.turf().getId());
	}

	@Test
	@DisplayName("no turfs gives no nearest")
	void emptyIsNull() {
		assertNull(TurfGeometry.nearest(List.of(), 0, 0));
	}
}
