package me.luckyraven.ray;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class RayTrace {

	public static Location cast(Player player, double x, double y, double z) {
		World    world         = player.getWorld();
		Location startLocation = player.getEyeLocation().clone().add(player.getLocation().getDirection().multiply(0.5));
		Vector   direction     = startLocation.getDirection();

		double maxDistance = player.getWorld().getViewDistance() * 16; // Assuming the view distance is set in chunks

		for (double distance = 0.5; distance <= maxDistance; distance += 0.5) {
			Location checkLocation = startLocation.clone().add(direction.clone().multiply(distance));

			if (!world.isChunkLoaded(checkLocation.getBlockX() >> 4, checkLocation.getBlockZ() >> 4)) {
				// If the chunk is not loaded, stop the ray casting
				break;
			}

			for (Entity entity : player.getWorld().getNearbyEntities(checkLocation, x, y, z)) {
				// Check if the ray intersects with an entity
				if (entity instanceof LivingEntity && !entity.equals(player)) {
					return checkLocation;
				}
			}

			if (!world.getBlockAt(checkLocation).isPassable()) {
				// If a non-passable block is hit, return the location
				return checkLocation;
			}
		}

		// If no block or entity is hit within the render distance, return null or handle accordingly
		return null;
	}

}
