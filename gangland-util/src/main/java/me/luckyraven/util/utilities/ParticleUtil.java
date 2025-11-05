package me.luckyraven.util.utilities;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public class ParticleUtil {

	public static <T> void spawnLine(Location from, Location to, Particle particle, int points, T data) {
		World world = from.getWorld();

		if (world == null || !world.equals(to.getWorld()))
			throw new IllegalArgumentException("Locations must be in the same world");

		double deltaX = (to.getX() - from.getX()) / points;
		double deltaY = (to.getY() - from.getY()) / points;
		double deltaZ = (to.getZ() - from.getZ()) / points;

		for (int i = 0; i <= points; i++) {
			double x = from.getX() + (deltaX * i);
			double y = from.getY() + (deltaY * i);
			double z = from.getZ() + (deltaZ * i);

			Location particleLocation = new Location(world, x, y, z);

			world.spawnParticle(particle, particleLocation, 1, data);
		}
	}

}
