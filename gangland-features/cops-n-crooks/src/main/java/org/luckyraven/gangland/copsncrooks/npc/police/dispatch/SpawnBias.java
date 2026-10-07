package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Where reinforcements should appear after a hand-off (set by the hand-off, consumed by dispatch): ahead of the
 * suspect's heading, until {@code until}.
 *
 * @param heading     the suspect's travel direction (only X/Z are read).
 * @param lastSeen    the suspect's position at the hand-off (newest heading sample): the seed new units report, never
 *                    his live position.
 * @param until       radio-clock ms the bias stops applying to newly enqueued units.
 * @param coneDegrees half-angle, either side of the heading, of "ahead".
 * @since 0.16.0
 */
public record SpawnBias(Vector heading, Location lastSeen, long until, double coneDegrees) {

	public SpawnBias {
		heading  = Objects.requireNonNull(heading, "heading").clone();
		lastSeen = Objects.requireNonNull(lastSeen, "lastSeen").clone();
	}

	/** The bias still applies at {@code now}. */
	public boolean activeAt(long now) {
		return now < until;
	}

	/**
	 * The horizontal angle between the heading and {@code spot - from} is at most {@code coneDegrees}. A heading or an
	 * offset without horizontal length has no direction to compare and counts as ahead.
	 */
	public boolean ahead(Location from, Location spot) {
		Vector toward = new Vector(spot.getX() - from.getX(), 0, spot.getZ() - from.getZ());
		Vector facing = new Vector(heading.getX(), 0, heading.getZ());
		if (toward.lengthSquared() == 0 || facing.lengthSquared() == 0) return true;
		return Math.toDegrees(facing.angle(toward)) <= coneDegrees;
	}
}
