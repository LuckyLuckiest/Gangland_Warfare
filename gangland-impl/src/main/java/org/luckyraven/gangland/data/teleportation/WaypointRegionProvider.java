package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Location;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionProvider;
import org.luckyraven.gangland.data.region.RegionShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Publishes every gang's {@code GANG} waypoint as a hideout place: a sphere of its radius (8 blocks when the radius is
 * 0, never more than {@link #MAX_RADIUS}) owned by the gang. Reads the manager on each call, so a waypoint added or
 * removed in game is visible at once.
 */
public final class WaypointRegionProvider implements RegionProvider {

	public static final String SOURCE         = "waypoint";
	public static final double DEFAULT_RADIUS = 8.0;
	public static final double MAX_RADIUS     = 64.0;

	private final WaypointManager waypoints;

	public WaypointRegionProvider(WaypointManager waypoints) {
		this.waypoints = waypoints;
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public List<PlaceRegion> regionsAt(Location at) {
		List<PlaceRegion> regions = new ArrayList<>();
		if (at == null || at.getWorld() == null) return regions;

		for (Waypoint waypoint : waypoints.getWaypoints().values()) {
			if (waypoint.getType() != Waypoint.WaypointType.GANG || waypoint.getGangId() == -1) continue;

			Location center = waypoint.getLocation();
			if (center == null || center.getWorld() == null) continue;

			double radius = waypoint.getRadius() > 0 ? Math.min(waypoint.getRadius(), MAX_RADIUS) : DEFAULT_RADIUS;
			PlaceRegion region = new PlaceRegion(SOURCE + ":" + waypoint.getUsedId(), waypoint.getName(),
			                                     center.getWorld().getName(),
			                                     new RegionShape.Sphere(center.getX(), center.getY(), center.getZ(),
			                                                            radius),
			                                     waypoint.getGangId(), Set.of(PlaceRegion.TAG_HIDEOUT));
			if (region.contains(at)) regions.add(region);
		}

		return regions;
	}

}
