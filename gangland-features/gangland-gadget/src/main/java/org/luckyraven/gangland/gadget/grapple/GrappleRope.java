package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rope math behind the grapple swing: plain {@link Vector} arithmetic, no world or entity calls, so it is unit
 * tested directly. Units are blocks and blocks per tick.
 * <p>
 * The rope is an inextensible constraint, not a tractor beam: gravity and the player's own tangential velocity are
 * left alone (they swing around the anchor like a pendulum), only motion that would stretch the rope is removed, and
 * any distance already past the rope length is pulled back through velocity, never by teleporting.
 */
public final class GrappleRope {

	private GrappleRope() {
	}

	/**
	 * The velocity to give a player at {@code position} moving at {@code velocity} on a rope of {@code ropeLength}
	 * tied to {@code anchor}, or {@code null} when the rope is slack (closer than the rope length), meaning no force
	 * at all. When taut: the outward radial component is removed, the excess beyond the rope length is added back
	 * toward the anchor, and the result is capped at {@code maxSpeed}.
	 */
	@Nullable
	public static Vector constrain(Vector position, Vector velocity, Vector anchor, double ropeLength,
	                               double maxSpeed) {
		Vector inward   = anchor.clone().subtract(position);
		double distance = inward.length();
		if (distance < ropeLength || distance == 0) return null;

		inward.multiply(1.0 / distance);

		Vector result = velocity.clone();
		double radial = result.dot(inward);
		if (radial < 0) {
			result.subtract(inward.clone().multiply(radial));
		}
		result.add(inward.multiply(distance - ropeLength));

		if (result.lengthSquared() > maxSpeed * maxSpeed) {
			result.normalize().multiply(maxSpeed);
		}
		return result;
	}

	/**
	 * Reels the rope in by {@code reelSpeed}, never shorter than {@code minLength} and never longer than it already is
	 * (a rope that latched on shorter than the minimum stays as it is).
	 */
	public static double reel(double ropeLength, double reelSpeed, double minLength) {
		return Math.max(ropeLength - reelSpeed, Math.min(minLength, ropeLength));
	}
}
