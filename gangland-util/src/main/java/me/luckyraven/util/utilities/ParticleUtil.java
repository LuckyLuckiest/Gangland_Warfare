package me.luckyraven.util.utilities;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;

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

	public static void createBloodSplash(Entity entity, double damage) {
		Location location = entity.getLocation().add(0, entity.getHeight() / 2, 0);
		World    world    = location.getWorld();

		if (world == null) return;

		// Scale particle count based on damage (minimum 5, scales up with damage)
		int    particleCount = Math.max(5, (int) (damage * 2));
		double spread        = 0.3;

		// Use XParticle for cross-version compatibility
		Particle particle = XParticle.DUST.get();
		if (particle == null) particle = Particle.DUST;

		// change the color
		Particle.DustOptions redColor = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
		world.spawnParticle(particle, location, particleCount, spread, spread, spread, 0.1, redColor);
	}

}
