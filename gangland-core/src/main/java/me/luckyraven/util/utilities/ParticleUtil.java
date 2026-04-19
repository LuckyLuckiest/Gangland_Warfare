package me.luckyraven.util.utilities;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;
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

	/**
	 * Spawns a dome of rising flames across {@code radius} around {@code center} — layered on top of an explosion burst
	 * to signal a fire-tick throwable (molotov etc.). A lava-drop accent at the epicentre reads as the ignition
	 * source.
	 */
	public static void spawnFireBurst(Location center, double radius) {
		World world = center.getWorld();
		if (world == null) return;

		Particle flame = XParticle.FLAME.get();
		if (flame == null) flame = Particle.FLAME;
		Particle lava = XParticle.LAVA.get();
		if (lava == null) lava = Particle.LAVA;

		ThreadLocalRandom rng    = ThreadLocalRandom.current();
		int               points = Math.max(40, (int) (radius * 18));

		for (int i = 0; i < points; i++) {
			double r     = Math.sqrt(rng.nextDouble()) * radius;
			double theta = rng.nextDouble() * 2 * Math.PI;
			double x     = center.getX() + Math.cos(theta) * r;
			double z     = center.getZ() + Math.sin(theta) * r;
			double y     = center.getY() + rng.nextDouble() * 0.3;

			world.spawnParticle(flame, new Location(world, x, y, z), 1, 0.05, 0.05, 0.05, 0.08, null);
		}

		world.spawnParticle(lava, center, Math.max(4, (int) radius * 2), 0.2, 0.1, 0.2, 0, null);
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
		Random random          = new Random();
		int    flameCount      = 3;
		int    leftFlameCount  = random.nextInt(flameCount, flameCount * 2);
		int    rightFlameCount = random.nextInt(flameCount, flameCount * 2);
		world.spawnParticle(flame, leftNozzle, leftFlameCount, 0.05, 0.05, 0.05, 0.02, null);
		world.spawnParticle(flame, rightNozzle, rightFlameCount, 0.05, 0.05, 0.05, 0.02, null);

		// Smoke trail slightly below flame
		int      smokeCount      = 2;
		int      leftSmokeCount  = random.nextInt(smokeCount, smokeCount * 2);
		int      rightSmokeCount = random.nextInt(smokeCount, smokeCount * 2);
		Location leftSmoke       = leftNozzle.clone().add(0, -0.3, 0);
		Location rightSmoke      = rightNozzle.clone().add(0, -0.3, 0);
		world.spawnParticle(smoke, leftSmoke, leftSmokeCount, 0.08, 0.08, 0.08, 0.01, null);
		world.spawnParticle(smoke, rightSmoke, rightSmokeCount, 0.08, 0.08, 0.08, 0.01, null);
	}

	/**
	 * Spawns weak flame and thick smoke from both jetpack nozzles during glide mode, making it visually clear to others
	 * that the player is using a jetpack.
	 */
	public static void spawnJetpackGlide(Player player) {
		Location loc   = player.getLocation();
		World    world = loc.getWorld();
		if (world == null) return;

		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		Vector direction = loc.getDirection().setY(0).normalize();
		Vector right     = direction.clone().crossProduct(new Vector(0, 1, 0));
		if (right.lengthSquared() < 0.001) {
			right = direction.clone().crossProduct(new Vector(1, 0, 0));
		}
		right.normalize();

		Vector   behind      = direction.clone().multiply(-0.4);
		Location leftNozzle  = loc.clone().add(behind).add(right.clone().multiply(-0.3)).add(0, 0.8, 0);
		Location rightNozzle = loc.clone().add(behind).add(right.clone().multiply(0.3)).add(0, 0.8, 0);

		// Heavy smoke trail below nozzles
		Random   random     = new Random();
		int      count      = 4;
		int      leftCount  = random.nextInt(count, count * 2);
		int      rightCount = random.nextInt(count, count * 2);
		Location leftSmoke  = leftNozzle.clone().add(0, -0.3, 0);
		Location rightSmoke = rightNozzle.clone().add(0, -0.3, 0);

		world.spawnParticle(smoke, leftSmoke, leftCount, 0.12, 0.10, 0.12, 0.02, null);
		world.spawnParticle(smoke, rightSmoke, rightCount, 0.12, 0.10, 0.12, 0.02, null);
	}

	/**
	 * Spawns thick tire-smoke clouds at both rear wheels during a car burnout (W+S held simultaneously).
	 *
	 * @param vehicleLocation current location of the vehicle entity
	 * @param yaw current heading of the vehicle in degrees
	 */
	public static void spawnBurnoutSmoke(Location vehicleLocation, float yaw) {
		World world = vehicleLocation.getWorld();
		if (world == null) return;

		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		double radians = Math.toRadians(yaw);
		double behindX = Math.sin(radians) * 0.7;
		double behindZ = -Math.cos(radians) * 0.7;
		// Left/right offset perpendicular to the heading
		double rightX = Math.cos(radians) * 0.4;
		double rightZ = Math.sin(radians) * 0.4;

		Location leftWheel  = vehicleLocation.clone().add(behindX - rightX, 0.1, behindZ - rightZ);
		Location rightWheel = vehicleLocation.clone().add(behindX + rightX, 0.1, behindZ + rightZ);

		world.spawnParticle(smoke, leftWheel, 5, 0.12, 0.08, 0.12, 0.02, null);
		world.spawnParticle(smoke, rightWheel, 5, 0.12, 0.08, 0.12, 0.02, null);
	}

	/**
	 * Spawns smoke exhaust particles at the rear of a car. Pass {@code leftNozzle} and/or {@code rightNozzle} as
	 * {@code true} to emit from that side; at least one must be true.
	 *
	 * @param vehicleLocation current location of the vehicle entity
	 * @param yaw current heading of the vehicle in degrees
	 * @param leftNozzle emit from the left exhaust nozzle
	 * @param rightNozzle emit from the right exhaust nozzle
	 */
	public static void spawnCarExhaust(Location vehicleLocation, float yaw, boolean leftNozzle, boolean rightNozzle) {
		World world = vehicleLocation.getWorld();
		if (world == null) return;

		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;

		double radians = Math.toRadians(yaw);
		double behindX = Math.sin(radians) * 0.7;
		double behindZ = -Math.cos(radians) * 0.7;
		double rightX  = Math.cos(radians) * 0.3;
		double rightZ  = Math.sin(radians) * 0.3;

		if (leftNozzle) {
			world.spawnParticle(smoke, vehicleLocation.clone().add(behindX + rightX, 0.3, behindZ + rightZ), 2, 0.05,
			                    0.05, 0.05, 0.008, null);
		}
		if (rightNozzle) {
			world.spawnParticle(smoke, vehicleLocation.clone().add(behindX - rightX, 0.3, behindZ - rightZ), 2, 0.05,
			                    0.05, 0.05, 0.008, null);
		}
	}

	/**
	 * Spawns a convincing flashbang detonation burst: a large central explosion, bright white radiating dust, trailing
	 * end-rod sparks, and a lingering smoke puff. Designed to visually sell a stun grenade going off.
	 */
	public static void spawnFlashbangBurst(Location center, double radius) {
		World world = center.getWorld();
		if (world == null) return;

		// Central bang — large explosion visual
		Particle explosionEmitter = XParticle.EXPLOSION_EMITTER.get();
		if (explosionEmitter != null) {
			world.spawnParticle(explosionEmitter, center, 1, 0, 0, 0, 0, null);
		} else {
			Particle explosion = XParticle.EXPLOSION.get();
			if (explosion != null) {
				world.spawnParticle(explosion, center, 3, 0.2, 0.2, 0.2, 0, null);
			}
		}

		// Bright white dust sphere radiating outward
		Particle dust = XParticle.DUST.get();
		if (dust != null) {
			Particle.DustOptions whiteFlash = new Particle.DustOptions(Color.WHITE, 3.5F);
			ThreadLocalRandom    rng        = ThreadLocalRandom.current();
			for (int i = 0; i < 40; i++) {
				double theta = rng.nextDouble() * Math.PI;
				double phi   = rng.nextDouble() * 2 * Math.PI;
				double r     = rng.nextDouble() * radius;
				double x     = r * Math.sin(theta) * Math.cos(phi);
				double y     = r * Math.sin(theta) * Math.sin(phi);
				double z     = r * Math.cos(theta);
				world.spawnParticle(dust, center.clone().add(x, y + 0.5, z), 1, whiteFlash);
			}
		}

		// Bright white trailing sparks
		Particle endRod = XParticle.END_ROD.get();
		if (endRod != null) {
			world.spawnParticle(endRod, center, 25, 0.8, 0.8, 0.8, 0.15, null);
		}

		// Aftermath smoke puff
		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;
		world.spawnParticle(smoke, center, 15, 0.5, 0.5, 0.5, 0.05, null);
	}

	/**
	 * Spawns one tick's worth of a dense, lingering smoke cloud. Uses tall signal-smoke columns for far-away
	 * visibility, large smoke for volume, and cloud particles for white fog opacity. Call every tick from a
	 * {@code RepeatingTimer}.
	 *
	 * @param center cloud center
	 * @param radius cloud radius in blocks
	 * @param intensity 0.0-1.0, allows ramping down near end of cloud lifetime
	 */
	public static void spawnDenseSmokeCloud(Location center, double radius, double intensity) {
		World world = center.getWorld();
		if (world == null) return;

		ThreadLocalRandom rng   = ThreadLocalRandom.current();
		int               count = Math.max(2, (int) (5 * intensity));

		// Tall lingering columns — visible from far away, rise high
		Particle signalSmoke = XParticle.CAMPFIRE_SIGNAL_SMOKE.get();
		if (signalSmoke != null) {
			for (int i = 0; i < count; i++) {
				double dx = (rng.nextDouble() * 2 - 1) * radius * 0.7;
				double dz = (rng.nextDouble() * 2 - 1) * radius * 0.7;
				world.spawnParticle(signalSmoke, center.clone().add(dx, 0.2, dz),
				                    1, 0.15, 0.1, 0.15, 0.01, null);
			}
		}

		// Large smoke — thick dark volume fill
		Particle largeSmoke = XParticle.LARGE_SMOKE.get();
		if (largeSmoke != null) {
			for (int i = 0; i < count; i++) {
				double dx = (rng.nextDouble() * 2 - 1) * radius;
				double dy = (rng.nextDouble() * 2 - 1) * radius * 0.4;
				double dz = (rng.nextDouble() * 2 - 1) * radius;
				world.spawnParticle(largeSmoke, center.clone().add(dx, dy + 0.6, dz),
				                    1, 0.08, 0.06, 0.08, 0.005, null);
			}
		}

		// Regular smoke — scattered through the volume for density
		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;
		for (int i = 0; i < count * 2; i++) {
			double dx = (rng.nextDouble() * 2 - 1) * radius;
			double dy = (rng.nextDouble() * 2 - 1) * radius * 0.5;
			double dz = (rng.nextDouble() * 2 - 1) * radius;
			world.spawnParticle(smoke, center.clone().add(dx, dy + 0.4, dz),
			                    1, 0.05, 0.05, 0.05, 0.008, null);
		}

		// White fog — cloud particles for thick opacity in the core
		Particle cloud = XParticle.CLOUD.get();
		if (cloud != null) {
			for (int i = 0; i < count; i++) {
				double dx = (rng.nextDouble() * 2 - 1) * radius * 0.6;
				double dy = rng.nextDouble() * radius * 0.35;
				double dz = (rng.nextDouble() * 2 - 1) * radius * 0.6;
				world.spawnParticle(cloud, center.clone().add(dx, dy + 0.3, dz),
				                    1, 0.06, 0.04, 0.06, 0.003, null);
			}
		}

		// Campfire cosy smoke — low-lying wispy fill between the columns
		Particle cosySmoke = XParticle.CAMPFIRE_COSY_SMOKE.get();
		if (cosySmoke != null) {
			for (int i = 0; i < count; i++) {
				double dx = (rng.nextDouble() * 2 - 1) * radius * 0.8;
				double dz = (rng.nextDouble() * 2 - 1) * radius * 0.8;
				world.spawnParticle(cosySmoke, center.clone().add(dx, 0.3, dz),
				                    1, 0.1, 0.05, 0.1, 0.008, null);
			}
		}
	}

	/**
	 * Spawns the initial burst when a smoke grenade detonates — a sudden outward expansion of smoke before the
	 * lingering cloud settles in. Called once at detonation, not per-tick.
	 */
	public static void spawnSmokeCloudBurst(Location center, double radius) {
		World world = center.getWorld();
		if (world == null) return;

		// Large smoke — main burst expanding outward
		Particle largeSmoke = XParticle.LARGE_SMOKE.get();
		if (largeSmoke == null) {
			largeSmoke = XParticle.SMOKE.get();
			if (largeSmoke == null) largeSmoke = Particle.SMOKE;
		}
		world.spawnParticle(largeSmoke, center, 40, radius * 0.5, radius * 0.3, radius * 0.5, 0.1, null);

		// Regular smoke — mixed in for density
		Particle smoke = XParticle.SMOKE.get();
		if (smoke == null) smoke = Particle.SMOKE;
		world.spawnParticle(smoke, center, 25, radius * 0.6, radius * 0.4, radius * 0.6, 0.06, null);

		// Cloud fog — white opacity in the core
		Particle cloud = XParticle.CLOUD.get();
		if (cloud != null) {
			world.spawnParticle(cloud, center, 25, radius * 0.4, radius * 0.2, radius * 0.4, 0.06, null);
		}

		// Signal smoke columns — tall plumes rising from the burst
		Particle signalSmoke = XParticle.CAMPFIRE_SIGNAL_SMOKE.get();
		if (signalSmoke != null) {
			world.spawnParticle(signalSmoke, center, 8, radius * 0.3, 0.1, radius * 0.3, 0.02, null);
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
		Particle particle = XParticle.BLOCK.get();
		if (particle == null) particle = Particle.BLOCK;

		// change the color
		Material material = XMaterial.REDSTONE_BLOCK.get();
		if (material == null) material = Material.REDSTONE_BLOCK;

		BlockData blockData = material.createBlockData();
		world.spawnParticle(particle, location, particleCount, spread, spread, spread, 0.1, blockData);
	}

}
