package me.luckyraven.weapon.projectile.type;

import com.cryptomorin.xseries.particles.XParticle;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Rocket extends WeaponProjectile<Fireball> {

	private final JavaPlugin     plugin;
	private       Fireball       rocket;
	private       RepeatingTimer trailEffect;

	public Rocket(JavaPlugin plugin, LivingEntity shooter, Weapon weapon) {
		super(plugin, shooter, weapon, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
			  Fireball.class);

		this.plugin = plugin;
	}

	public void startSmokeTrail(Fireball rocket) {
		this.rocket = rocket;

		trailEffect = new RepeatingTimer(plugin, 1L, task -> {
			if (rocket.isDead() || !rocket.isValid()) {
				task.stop();
				return;
			}

			Location location = rocket.getLocation();
			World    world    = location.getWorld();

			if (world == null) return;

			// smoke trail
			world.spawnParticle(XParticle.SMOKE.get(), location, 3, 0.1, 0.1, 0.1, 0.01);

			// fire particles
			world.spawnParticle(XParticle.FLAME.get(), location, 2, 0.05, 0.05, 0.05, 0.01);

			// occasional spark
			if (Math.random() < 0.3) {
				world.spawnParticle(XParticle.LAVA.get(), location, 1, 0.05, 0.05, 0.05, 0);
			}
		});
	}

	public void createExplosion(Location location, float power) {
		World world = location.getWorld();

		if (world == null) return;

		// Create explosion
		world.createExplosion(location, power, false, false, getShooter());

		// Add visual effects
		world.spawnParticle(XParticle.EXPLOSION.get(), location, 5, 0.5, 0.5, 0.5, 0.1);
		world.spawnParticle(XParticle.SMOKE.get(), location, 30, 1.0, 1.0, 1.0, 0.1);
		world.spawnParticle(XParticle.FLAME.get(), location, 20, 1.0, 1.0, 1.0, 0.1);

		// Play explosion sound
		world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

		// Stop trail effect if active
		if (trailEffect != null) {
			trailEffect.cancel();
		}
	}

	public void startHoming(LivingEntity target, double homingStrength) {
		if (rocket == null || rocket.isDead()) return;

		new RepeatingTimer(plugin, 2, task -> {
			if (rocket.isDead() || !rocket.isValid() || target.isDead()) {
				task.cancel();
				return;
			}

			Vector currentVelocity = rocket.getVelocity();
			Vector direction = target.getEyeLocation()
									 .toVector()
									 .subtract(rocket.getLocation().toVector())
									 .normalize();

			// Blend current velocity with target direction
			Vector newVelocity = currentVelocity.multiply(1 - homingStrength)
												.add(direction.multiply(homingStrength))
												.normalize();
			rocket.setVelocity(newVelocity.multiply(currentVelocity.length()));
		});
	}

}
