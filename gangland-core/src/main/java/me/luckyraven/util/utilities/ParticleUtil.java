package me.luckyraven.util.utilities;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

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

	/**
	 * Spawns flame particles in a cone with upward drift, simulating a real flamethrower. Multiple rays fan out within
	 * the given angle; each ray's particles drift upward with distance.
	 */
	public static void spawnFlameCone(Location eye, Vector direction, double range, double coneAngleDegrees) {
		World world = eye.getWorld();
		if (world == null) return;

		Particle flame = XParticle.FLAME.get();
		if (flame == null) flame = Particle.FLAME;

		ThreadLocalRandom rng       = ThreadLocalRandom.current();
		double            halfAngle = Math.toRadians(coneAngleDegrees / 2.0);
		int               rays      = Math.max(3, (int) (coneAngleDegrees / 8));

		Vector norm = direction.clone().normalize();

		// build an orthonormal basis perpendicular to the direction
		Vector perp1 = norm.clone().crossProduct(new Vector(0, 1, 0));
		if (perp1.lengthSquared() < 0.001) {
			perp1 = norm.clone().crossProduct(new Vector(1, 0, 0));
		}
		perp1.normalize();
		Vector perp2 = norm.clone().crossProduct(perp1).normalize();

		for (int r = 0; r < rays; r++) {
			// random radial offset and rotation within the cone
			double theta = rng.nextDouble() * halfAngle;
			double phi   = rng.nextDouble() * 2 * Math.PI;
			Vector rayDir = norm.clone()
			                    .add(perp1.clone().multiply(Math.sin(theta) * Math.cos(phi)))
			                    .add(perp2.clone().multiply(Math.sin(theta) * Math.sin(phi)))
			                    .normalize();

			int points = Math.max(1, (int) (range * 2.5));
			for (int i = 0; i <= points; i++) {
				double   t   = (double) i / points;
				Location loc = eye.clone().add(rayDir.clone().multiply(t * range));
				// flames drift upward more as they travel farther
				loc.add(0, t * t * 0.6 + rng.nextDouble() * 0.05, 0);
				world.spawnParticle(flame, loc, 1, 0.02, 0.02, 0.02, 0.01, null);
			}
		}
	}

	/**
	 * Spawns a diagonal slash arc in front of the player — used for melee weapons.
	 */
	public static void spawnSlashArc(Location center, Vector direction, double radius) {
		World world = center.getWorld();
		if (world == null) return;

		Particle crit = XParticle.CRIT.get();
		if (crit == null) crit = Particle.CRIT;

		Vector norm  = direction.clone().normalize();
		Vector right = norm.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) {
			right = norm.clone().crossProduct(new Vector(1, 0, 0));
		}
		right.normalize();

		// arc in front of player: diagonal sweep from lower-right to upper-left
		Location base   = center.clone().add(norm.multiply(1.2)).add(0, 1.0, 0);
		int      points = 8;
		for (int i = 0; i <= points; i++) {
			double   t   = (double) i / points - 0.5;  // -0.5 to 0.5
			Location loc = base.clone().add(right.clone().multiply(t * radius * 2)).add(0, t * radius * 1.5, 0);
			world.spawnParticle(crit, loc, 1, 0, 0, 0, 0, null);
		}
	}

	/**
	 * Spawns a small smoke puff at a location — used for grenade trail particles.
	 */
	public static void spawnSmokeTrail(Location location) {
		World world = location.getWorld();
		if (world == null) return;

		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		world.spawnParticle(smoke, location, 2, 0.05, 0.05, 0.05, 0.01, null);
	}

	/**
	 * Spawns an expanding ring of enchantment particles around the player to show charge level.
	 */
	public static void spawnChargeRing(Location center, int chargeLevel, int maxLevel) {
		if (chargeLevel <= 0) return;
		World world = center.getWorld();
		if (world == null) return;

		Particle enchant = XParticle.ENCHANT.get();
		if (enchant == null) enchant = Particle.ENCHANT;

		double radius = 0.4 + (chargeLevel / (double) maxLevel) * 0.9;
		int    points = 8 + chargeLevel * 4;
		for (int i = 0; i < points; i++) {
			double   angle = (2 * Math.PI / points) * i;
			double   x     = Math.cos(angle) * radius;
			double   z     = Math.sin(angle) * radius;
			Location loc   = center.clone().add(x, 1.0, z);
			world.spawnParticle(enchant, loc, 1, 0, 0, 0, 0, null);
		}
	}

	/**
	 * Spawns an outward sphere pulse of enchantment particles at the given radius — used for biological weapon
	 * area-of-effect release.
	 */
	public static void spawnAreaPulse(Location center, double radius) {
		World world = center.getWorld();
		if (world == null) return;

		Particle enchant = XParticle.ENCHANT.get();
		if (enchant == null) enchant = Particle.ENCHANT;

		int    points = 24;
		double step   = Math.PI / (points / 2.0);
		for (double theta = 0; theta < Math.PI; theta += step) {
			double r = Math.sin(theta) * radius;
			for (double phi = 0; phi < 2 * Math.PI; phi += step) {
				double   x   = r * Math.cos(phi);
				double   y   = Math.cos(theta) * radius;
				double   z   = r * Math.sin(phi);
				Location loc = center.clone().add(x, y + 1.0, z);
				world.spawnParticle(enchant, loc, 1, 0, 0, 0, 0, null);
			}
		}
	}

	public static void spawnBeam(Location from, Location to) {
		if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

		Particle enchant = XParticle.ENCHANT.get();
		if (enchant == null) enchant = Particle.ENCHANT;

		int points = Math.max(2, (int) (from.distance(to) * 4));
		spawnLine(from, to, enchant, points, null);
	}

	public static void spawnExplosionBurst(Location center) {
		World world = center.getWorld();
		if (world == null) return;

		Particle explosion = XParticle.EXPLOSION.get();
		if (explosion == null) explosion = Particle.EXPLOSION;

		world.spawnParticle(explosion, center, 1, 0, 0, 0, 0, null);
	}

	public static void spawnFireSpray(Location eye, Vector direction, double range) {
		Particle flame = XParticle.FLAME.get();
		if (flame == null) flame = Particle.FLAME;

		Location target = eye.clone().add(direction.clone().normalize().multiply(range));
		spawnLine(eye, target, flame, Math.max(1, (int) (range * 2)), null);
	}

	/**
	 * Spawns two flame trails from behind the player's back, simulating jetpack exhaust. Trails originate from slightly
	 * behind and below the player's shoulders, offset left and right.
	 */
	public static void spawnJetpackFlame(Player player) {
		Location loc   = player.getLocation();
		World    world = loc.getWorld();
		if (world == null) return;

		Particle flame = XParticle.FLAME.get();
		if (flame == null) flame = Particle.FLAME;
		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		Vector direction = loc.getDirection().setY(0).normalize();
		Vector right     = direction.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) {
			right = direction.clone().crossProduct(new Vector(1, 0, 0));
		}
		right.normalize();

		// Behind player, offset left and right for two exhaust nozzles
		Vector   behind      = direction.clone().multiply(-0.4);
		Location leftNozzle  = loc.clone().add(behind).add(right.clone().multiply(-0.3)).add(0, 0.8, 0);
		Location rightNozzle = loc.clone().add(behind).add(right.clone().multiply(0.3)).add(0, 0.8, 0);

		// Flame core
		world.spawnParticle(flame, leftNozzle, 3, 0.05, 0.05, 0.05, 0.02, null);
		world.spawnParticle(flame, rightNozzle, 3, 0.05, 0.05, 0.05, 0.02, null);

		// Smoke trail slightly below flame
		Location leftSmoke  = leftNozzle.clone().add(0, -0.3, 0);
		Location rightSmoke = rightNozzle.clone().add(0, -0.3, 0);
		world.spawnParticle(smoke, leftSmoke, 2, 0.08, 0.08, 0.08, 0.01, null);
		world.spawnParticle(smoke, rightSmoke, 2, 0.08, 0.08, 0.08, 0.01, null);
	}

	/**
	 * Spawns light smoke particles for jetpack glide mode (no fuel remaining).
	 */
	public static void spawnJetpackGlide(Player player) {
		Location loc   = player.getLocation();
		World    world = loc.getWorld();
		if (world == null) return;

		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		world.spawnParticle(smoke, loc.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.005, null);
	}

	public static void createBloodSplash(Entity entity, double damage) {
		Location location = entity.getLocation().add(0, entity.getHeight() / 2, 0);
		World    world    = location.getWorld();

		if (world == null) return;

		// Scale particle count based on damage (minimum 5, scales up with damage)
		int    particleCount = Math.max(5, (int) (damage * 2));
		double spread        = 0.3;

		// Use XParticle for cross-version compatibility
		Particle particle = XParticle.BLOCK.get();
		if (particle == null) particle = Particle.BLOCK;

		// change the color
		Material material = XMaterial.REDSTONE_BLOCK.get();
		if (material == null) material = Material.REDSTONE_BLOCK;

		BlockData blockData = material.createBlockData();
		world.spawnParticle(particle, location, particleCount, spread, spread, spread, 0.1, blockData);
	}

}
