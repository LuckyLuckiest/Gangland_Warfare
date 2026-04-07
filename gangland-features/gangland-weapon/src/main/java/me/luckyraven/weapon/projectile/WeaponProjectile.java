package me.luckyraven.weapon.projectile;

import com.cryptomorin.xseries.particles.XParticle;
import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.dto.SoundData;
import me.luckyraven.weapon.events.projectile.WeaponProjectileLaunchEvent;
import me.luckyraven.weapon.types.gun.GunWeapon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class WeaponProjectile<T extends Projectile> extends WProjectile {

	private final JavaPlugin plugin;
	private final GunWeapon  weapon;
	private final Class<T>   bulletType;

	public WeaponProjectile(JavaPlugin plugin, LivingEntity shooter, GunWeapon weapon, Location location,
	                        Vector velocity, Class<T> bulletType) {
		super(shooter, location, velocity);

		this.plugin     = plugin;
		this.weapon     = weapon;
		this.bulletType = bulletType;
	}

	@Override
	public Projectile launchProjectile() {
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

		// apply spread + speed to obtain the launch velocity
		Vector spread         = weapon.getSpread().applySpread(velocity);
		Vector launchVelocity = spread.multiply(getSpeed());

		return doLaunch(weaponPosition, launchVelocity);
	}

	/**
	 * Spawns this projectile at an explicit location with an explicit velocity, bypassing the shooter-relative
	 * weapon-position offset, spread, and speed multiplication. Used for ricochet relaunches where the modifier handler
	 * already computes the impact location and reflected velocity.
	 */
	public Projectile launchProjectile(Location spawnLocation, Vector launchVelocity) {
		return doLaunch(spawnLocation, launchVelocity);
	}

	@Override
	public double getSpeed() {
		return weapon.getProjectileData().getSpeed();
	}

	/**
	 * Shared spawn logic for both the normal shooter-relative launch and the explicit-position relaunch.
	 */
	private Projectile doLaunch(Location spawnLocation, Vector launchVelocity) {
		World world = spawnLocation.getWorld();
		if (world == null) {
			return null;
		}

		// Mirror the spawn location/velocity onto this WProjectile so any consumer reading getLocation()/
		// getVelocity() after the launch sees the values that were actually used.
		setLocation(spawnLocation.toVector());
		setVelocity(launchVelocity);

		Projectile projectile = world.spawn(spawnLocation, bulletType);

		projectile.setSilent(true);
		projectile.setGravity(false);
		projectile.setShooter(getShooter());
		projectile.setVelocity(launchVelocity);

		if (weapon.getProjectileData().isParticle() && getShooter() instanceof Player) {
			Location clone = spawnLocation.clone();
			Location endLocation = clone.add(
					launchVelocity.clone().normalize().multiply(weapon.getProjectileData().getDistance()));

			// Spawn particle line from spawn position following the projectile trajectory
			ParticleUtil.spawnLine(spawnLocation, endLocation, XParticle.DUST.get(),
			                       weapon.getProjectileData().getDistance(),
			                       new Particle.DustOptions(Color.GRAY.getBukkitColor(), 0.5F));
		}

		// call the projectile launch event
		WeaponProjectileLaunchEvent event = new WeaponProjectileLaunchEvent(weapon, projectile, this);
		Bukkit.getPluginManager().callEvent(event);

		startFlybyCheck(projectile);

		return projectile;
	}

	private void startFlybyCheck(Projectile projectile) {
		SoundData soundData  = weapon.getSoundData();
		double    flybyRange = soundData.getFlybyRange();

		if (flybyRange <= 0 || (soundData.getFlybyDefault() == null && soundData.getFlybyCustom() == null)) {
			return;
		}

		LivingEntity shooter = getShooter();
		Set<UUID>    heard   = new HashSet<>();
		RepeatingTimer timer = new RepeatingTimer(plugin, 1L, t -> {
			if (!projectile.isValid()) {
				t.stop();
				return;
			}

			for (Entity entity : projectile.getNearbyEntities(flybyRange, flybyRange, flybyRange)) {
				if (!(entity instanceof Player player)) continue;
				if (entity.equals(shooter)) continue;
				if (!heard.add(player.getUniqueId())) continue;

				SoundConfiguration.playSounds(player, soundData.getFlybyCustom(), soundData.getFlybyDefault());
			}
		});

		timer.start(false);
	}

	@NotNull
	private RepeatingTimer applyGravity(GunWeapon weapon, Projectile projectile) {
		AtomicReference<Location> initialLocation  = new AtomicReference<>(projectile.getLocation());
		AtomicDouble              furthestDistance = new AtomicDouble(weapon.getProjectileData().getDistance());
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
