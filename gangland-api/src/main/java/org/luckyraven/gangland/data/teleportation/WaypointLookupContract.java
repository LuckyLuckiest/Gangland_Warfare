package org.luckyraven.gangland.data.teleportation;

import java.util.Map;

/**
 * Read-only view of the host's waypoint registry, so a runtime module can resolve waypoints without naming
 * {@code WaypointManager} (which lives in the host jar, not in the api).
 */
public interface WaypointLookupContract {

	/**
	 * @return the waypoint registered under {@code name}, or {@code null} when there is none.
	 */
	Waypoint get(String name);

	/**
	 * @return every known waypoint, keyed by its generated id.
	 */
	Map<Integer, Waypoint> getWaypoints();

}
