package org.luckyraven.gangland.npcshops;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Player proximity and line-of-sight lookups shared by the banker and trader managers and remove commands. */
public final class NpcTargeting {

	private NpcTargeting() {
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
