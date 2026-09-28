package org.luckyraven.gangland.npc.radio;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Direction words for a radio line: which side of a viewer a spot lies on, and which of 8 compass words a bearing
 * falls closest to. Vector math only — no server calls, so it works against a plain, unmocked {@link Location}.
 *
 * @since 1.13.0
 */
public final class RadioSides {

	private static final double COS_45 = Math.cos(Math.toRadians(45));

	private RadioSides() {
	}

	/** front, left, right or behind, relative to a viewer's facing. */
	public enum Side {
		FRONT,
		LEFT,
		RIGHT,
		BEHIND
	}

	/**
	 * Which side of {@code viewer} (by its yaw) {@code where} lies on. Minecraft yaw 0 faces {@code +z}, so {@code
	 * +x} is the viewer's left.
	 */
	public static Side sideOf(Location viewer, Location where) {
		Vector forward = viewer.getDirection().setY(0);
		if (forward.lengthSquared() < 1e-6) forward = new Vector(0, 0, 1);
		else forward.normalize();

		Vector to = where.clone().subtract(viewer).toVector().setY(0);
		if (to.lengthSquared() < 1e-6) return Side.FRONT;
		to.normalize();

		double dot = forward.dot(to);
		if (dot > COS_45) return Side.FRONT;
		if (dot < -COS_45) return Side.BEHIND;

		double cross = forward.getX() * to.getZ() - forward.getZ() * to.getX();
		return cross < 0 ? Side.LEFT : Side.RIGHT;
	}

	/** The compass octant (0 = north, going clockwise) of the bearing from {@code from} to {@code to}. */
	public static int compass8(Location from, Location to) {
		double dx  = to.getX() - from.getX();
		double dz  = to.getZ() - from.getZ();
		long   idx = Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0);
		return (int) Math.floorMod(idx, 8);
	}
}
