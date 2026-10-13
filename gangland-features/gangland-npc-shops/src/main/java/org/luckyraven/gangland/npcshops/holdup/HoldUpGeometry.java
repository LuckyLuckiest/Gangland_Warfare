package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.util.Vector;

public final class HoldUpGeometry {

	private HoldUpGeometry() {
	}

	/**
	 * @param eyeDirection the player's looking direction
	 * @param eye          the player's eye position
	 * @param target       the trader's eye position
	 * @return true when target is within {@code range} blocks and the angle between the looking direction and the
	 * line to the target is at most {@code angleDegrees}
	 */
	public static boolean isAimed(Vector eyeDirection, Vector eye, Vector target, double range,
	                              double angleDegrees) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
