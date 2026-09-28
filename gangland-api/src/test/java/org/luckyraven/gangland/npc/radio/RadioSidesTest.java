package org.luckyraven.gangland.npc.radio;

import org.bukkit.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RadioSides - viewer-relative side and compass octant, vector math only")
class RadioSidesTest {

	private static Location at(double x, double y, double z, float yaw) {
		return new Location(null, x, y, z, yaw, 0f);
	}

	// yaw 0 faces +z, so +x is the viewer's left (pinned by the spec).

	@Test
	@DisplayName("yaw 0 (+z): +x is LEFT, -x is RIGHT, +z is FRONT, -z is BEHIND")
	void sideOf_yawZero() {
		Location viewer = at(0, 64, 0, 0f);
		assertEquals(RadioSides.Side.LEFT, RadioSides.sideOf(viewer, at(5, 64, 0, 0)));
		assertEquals(RadioSides.Side.RIGHT, RadioSides.sideOf(viewer, at(-5, 64, 0, 0)));
		assertEquals(RadioSides.Side.FRONT, RadioSides.sideOf(viewer, at(0, 64, 5, 0)));
		assertEquals(RadioSides.Side.BEHIND, RadioSides.sideOf(viewer, at(0, 64, -5, 0)));
	}

	@Test
	@DisplayName("yaw 90 (-x): the same four points rotate a quarter-turn")
	void sideOf_yaw90() {
		Location viewer = at(0, 64, 0, 90f);
		assertEquals(RadioSides.Side.FRONT, RadioSides.sideOf(viewer, at(-5, 64, 0, 0)));
		assertEquals(RadioSides.Side.BEHIND, RadioSides.sideOf(viewer, at(5, 64, 0, 0)));
		assertEquals(RadioSides.Side.LEFT, RadioSides.sideOf(viewer, at(0, 64, 5, 0)));
		assertEquals(RadioSides.Side.RIGHT, RadioSides.sideOf(viewer, at(0, 64, -5, 0)));
	}

	@Test
	@DisplayName("yaw 180 (-z): front/behind and left/right both flip from yaw 0")
	void sideOf_yaw180() {
		Location viewer = at(0, 64, 0, 180f);
		assertEquals(RadioSides.Side.RIGHT, RadioSides.sideOf(viewer, at(5, 64, 0, 0)));
		assertEquals(RadioSides.Side.LEFT, RadioSides.sideOf(viewer, at(-5, 64, 0, 0)));
		assertEquals(RadioSides.Side.BEHIND, RadioSides.sideOf(viewer, at(0, 64, 5, 0)));
		assertEquals(RadioSides.Side.FRONT, RadioSides.sideOf(viewer, at(0, 64, -5, 0)));
	}

	@Test
	@DisplayName("yaw 270 (+x): another quarter-turn")
	void sideOf_yaw270() {
		Location viewer = at(0, 64, 0, 270f);
		assertEquals(RadioSides.Side.BEHIND, RadioSides.sideOf(viewer, at(-5, 64, 0, 0)));
		assertEquals(RadioSides.Side.FRONT, RadioSides.sideOf(viewer, at(5, 64, 0, 0)));
		assertEquals(RadioSides.Side.RIGHT, RadioSides.sideOf(viewer, at(0, 64, 5, 0)));
		assertEquals(RadioSides.Side.LEFT, RadioSides.sideOf(viewer, at(0, 64, -5, 0)));
	}

	@Test
	@DisplayName("coincident points: FRONT, never a divide-by-zero")
	void sideOf_coincidentPoints_isFront() {
		Location viewer = at(3, 64, 3, 45f);
		assertEquals(RadioSides.Side.FRONT, RadioSides.sideOf(viewer, viewer.clone()));
	}

	@Test
	@DisplayName("compass8: the 8 headings from north (0), clockwise")
	void compass8_eightHeadings() {
		Location from = at(0, 64, 0, 0f);
		assertEquals(0, RadioSides.compass8(from, at(0, 64, -10, 0)));   // north
		assertEquals(1, RadioSides.compass8(from, at(10, 64, -10, 0)));  // north-east
		assertEquals(2, RadioSides.compass8(from, at(10, 64, 0, 0)));    // east
		assertEquals(3, RadioSides.compass8(from, at(10, 64, 10, 0)));   // south-east
		assertEquals(4, RadioSides.compass8(from, at(0, 64, 10, 0)));    // south
		assertEquals(5, RadioSides.compass8(from, at(-10, 64, 10, 0)));  // south-west
		assertEquals(6, RadioSides.compass8(from, at(-10, 64, 0, 0)));   // west
		assertEquals(7, RadioSides.compass8(from, at(-10, 64, -10, 0))); // north-west
	}
}
