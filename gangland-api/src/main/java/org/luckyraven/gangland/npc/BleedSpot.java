package org.luckyraven.gangland.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A wound spot on an NPC's body, as an offset from its feet that turns with its facing: {@code right} is blocks to the
 * NPC's own right (so LEFT_ARM and RIGHT_ARM mirror), {@code forward} blocks in front of it, {@code height} up.
 *
 * @since 0.13.0
 */
public enum BleedSpot {
	HEAD(0, 1.7, 0.0),
	CHEST(0, 1.25, 0.15),
	LEFT_ARM(-0.4, 1.2, 0.0),
	RIGHT_ARM(0.4, 1.2, 0.0),
	LEFT_LEG(-0.15, 0.4, 0.0),
	RIGHT_LEG(0.15, 0.4, 0.0);

	private final double right;
	private final double height;
	private final double forward;

	BleedSpot(double right, double height, double forward) {
		this.right   = right;
		this.height  = height;
		this.forward = forward;
	}

	/** {x, y, z} blocks from the feet for an NPC facing {@code yawDegrees} (Bukkit yaw: 0 faces +Z, 90 faces -X). */
	public double[] offset(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new double[]{-sin * forward - cos * right, height, cos * forward - sin * right};
	}

	/** The spots named in {@code names} (case-insensitive, unknown names skipped); every spot when none is valid. */
	public static List<BleedSpot> parse(List<String> names) {
		List<BleedSpot> spots = new ArrayList<>();
		for (String name : names) {
			try {
				BleedSpot spot = valueOf(name.trim().toUpperCase(Locale.ROOT));
				if (!spots.contains(spot)) spots.add(spot);
			} catch (IllegalArgumentException ignored) {
				// unknown spot name: skipped
			}
		}
		return spots.isEmpty() ? List.of(values()) : spots;
	}
}
