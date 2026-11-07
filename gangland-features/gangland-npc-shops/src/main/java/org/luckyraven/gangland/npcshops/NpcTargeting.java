package org.luckyraven.gangland.npcshops;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Player proximity and line-of-sight lookups shared by the banker and trader managers and remove commands. */
public final class NpcTargeting {

	private NpcTargeting() {
	}

	/** The entity the player is looking at within {@code maxDistance} blocks (never the player), or {@code null}. */
	@Nullable
	public static Entity rayTraceEntity(Player player, double maxDistance) {
		Location eye       = player.getEyeLocation();
		Vector   direction = eye.getDirection();

		RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, maxDistance,
		                                                           entity -> !entity.equals(player));
		return result != null ? result.getHitEntity() : null;
	}

	@Nullable
	public static Player findClosestPlayer(Location location, double radiusSquared) {
		Player closest     = null;
		double closestDist = radiusSquared;

		for (Player player : location.getWorld().getPlayers()) {
			double dist = player.getLocation().distanceSquared(location);
			if (dist < closestDist) {
				closest     = player;
				closestDist = dist;
			}
		}
		return closest;
	}
}
