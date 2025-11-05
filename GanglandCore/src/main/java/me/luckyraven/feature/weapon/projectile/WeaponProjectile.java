package me.luckyraven.feature.weapon.projectile;

import com.cryptomorin.xseries.particles.XParticle;
import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.ParticleUtil;
import me.luckyraven.color.Color;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.events.WeaponProjectileLaunchEvent;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class WeaponProjectile<T extends Projectile> extends WProjectile {

	private final JavaPlugin plugin;
	private final Weapon     weapon;
	private final Class<T>   bulletType;

	public WeaponProjectile(JavaPlugin plugin, LivingEntity shooter, Weapon weapon, Location location, Vector velocity,
							Class<T> bulletType) {
		super(shooter, location, velocity);

		this.plugin     = plugin;
		this.weapon     = weapon;
		this.bulletType = bulletType;
	}

	@Override
	public void launchProjectile() {
		// Get the eye location
		Location eyeLocation = getShooter().getEyeLocation();
		Vector   velocity    = getVelocity();

		// Calculate the weapon position offset (right side of the player)
		Vector rightVector = eyeLocation.getDirection()
										.getCrossProduct(new Vector(0, 1, 0))
										.normalize()
										.multiply(0.3); // Distance from center (adjust as needed)

		// Offset slightly downward to represent hand/weapon position
		Vector downVector = new Vector(0, -0.2, 0);

		// Calculate weapon position (side of player) - this is where projectile spawns
		Location weaponPosition = eyeLocation.clone().add(rightVector).add(downVector);

		// Spawn projectile at weapon position, not at center
		Projectile projectile = getShooter().getWorld().spawn(weaponPosition, bulletType);

		projectile.setSilent(true);
		projectile.setGravity(false);
		projectile.setShooter(getShooter());

		// apply spread
		Vector spread = weapon.getSpread().applySpread(velocity);

		// set the velocity according to the modified values
		setVelocity(spread.multiply(getSpeed()));
		projectile.setVelocity(getVelocity());

		if (weapon.isParticle()) {
			if (getShooter() instanceof Player) {
				// Calculate end location using the actual projectile velocity (with spread applied)
				Location endLocation = weaponPosition.clone()
													 .add(getVelocity().normalize()
																	   .multiply(weapon.getProjectileDistance()));

				// Spawn particle line from weapon position following the projectile trajectory
				ParticleUtil.spawnLine(weaponPosition, endLocation, XParticle.DUST.get(),
									   weapon.getProjectileDistance(),
									   new Particle.DustOptions(Color.GRAY.getBukkitColor(), 0.5F));
			}
		}

		// call the projectile launch event
		WeaponProjectileLaunchEvent event = new WeaponProjectileLaunchEvent(weapon, projectile, this);
		plugin.getServer().getPluginManager().callEvent(event);
	}

	@Override
	public double getSpeed() {
		return weapon.getProjectileSpeed();
	}

	@NotNull
	private RepeatingTimer applyGravity(Weapon weapon, Projectile projectile) {
		AtomicReference<Location> initialLocation  = new AtomicReference<>(projectile.getLocation());
		AtomicDouble              furthestDistance = new AtomicDouble(weapon.getProjectileDistance());
		AtomicBoolean             falling          = new AtomicBoolean();

		return new RepeatingTimer(plugin, 1L, t -> {
			if (projectile.isDead()) {
				t.cancel();
				return;
			}

			double distance = initialLocation.get().distance(projectile.getLocation());

			if (distance >= furthestDistance.get() && !falling.get()) falling.set(true);
			if (!falling.get()) return;

			Vector currentVelocity = projectile.getVelocity();
			double newY            = currentVelocity.getY() - 0.001;
			projectile.setVelocity(currentVelocity.setY(newY).normalize());

			if (projectile.getLocation().getChunk().isLoaded() ||
				(projectile.getLocation().getY() > 0 && distance < furthestDistance.get() * 10)) return;

			projectile.remove();
			t.cancel();
		});
	}
}
