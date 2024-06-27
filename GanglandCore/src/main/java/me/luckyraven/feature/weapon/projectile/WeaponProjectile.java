package me.luckyraven.feature.weapon.projectile;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.util.Pair;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class WeaponProjectile<T extends Projectile> extends WProjectile {

	// current player projectile count
	private static final Map<UUID, Long> sessionProjectileCount = new HashMap<>();

	// projectile launched by a specific player with the projectile instance itself
	private static final Map<UUID, Map<Long, Pair<WeaponProjectile<?>, Projectile>>> projectileMap = new HashMap<>();

	// projectile location according to the saved id
	private static final Map<UUID, Map<Long, Location>> shotLocation = new HashMap<>();

	private final JavaPlugin plugin;
	private final Weapon     weapon;
	private final Class<T>   bulletType;
	private final Random     random;

	public WeaponProjectile(JavaPlugin plugin, LivingEntity shooter, Weapon weapon, Location location, Vector velocity,
							Class<T> bulletType) {
		super(shooter, location, velocity);

		this.plugin     = plugin;
		this.weapon     = weapon;
		this.bulletType = bulletType;
		this.random     = new Random();
	}

	@Override
	public void launchProjectile() {
		// Get the eye location
		Location eyeLocation = getShooter().getEyeLocation();
		// spawn the projectile(s)
		for (int i = 0; i < weapon.getProjectilePerShot(); ++i) {
			Vector velocity = getVelocity();
			// calculate the spawn location based on the view direction
			Location   spawnLocation = eyeLocation.clone().add(eyeLocation.getDirection());
			Projectile projectile    = getShooter().getWorld().spawn(spawnLocation, bulletType);

			projectile.setSilent(true);
			projectile.setGravity(false);
			projectile.setShooter(getShooter());

			// apply spread
			Vector spread = applySpread(velocity, weapon.getSpreadStart());

			// set the velocity according to the modified values
			setVelocity(spread.multiply(getSpeed()));
			projectile.setVelocity(getVelocity());

			synchronized (sessionProjectileCount) {
				// record each projectile launched
				sessionProjectileCount.merge(getShooter().getUniqueId(), 1L, Long::sum);
				// current reached value
				long currentCount = sessionProjectileCount.get(getShooter().getUniqueId());

				// save the instance
				synchronized (projectileMap) {
					Map<Long, Pair<WeaponProjectile<?>, Projectile>> projectiles = new HashMap<>();

					projectiles.put(currentCount, new Pair<>(this, projectile));
					projectileMap.merge(getShooter().getUniqueId(), projectiles, (oldMap, map) -> {
						oldMap.putAll(map);
						return oldMap;
					});
				}

				// save the current player projectile lunch location
				synchronized (shotLocation) {
					Map<Long, Location> locationShot = new HashMap<>();

					locationShot.put(currentCount, spawnLocation);
					shotLocation.merge(getShooter().getUniqueId(), locationShot, (oldMap, map) -> {
						oldMap.putAll(map);
						return oldMap;
					});
				}
			}

			// update the projectile position
			RepeatingTimer timer = new RepeatingTimer(plugin, 20L, time -> {
				Map<Long, Pair<WeaponProjectile<?>, Projectile>> weaponProjectiles = projectileMap.get(
						getShooter().getUniqueId());

				for (Pair<WeaponProjectile<?>, Projectile> weaponProjectilePair : weaponProjectiles.values()) {
					WeaponProjectile<?> weaponProjectile = weaponProjectilePair.first();
					Projectile          thrownProjectile = weaponProjectilePair.second();

//				double distanceTravelled = shotLocation.get(getShooter().getUniqueId()).distance(weaponProjectile.getLocation());

//				weaponProjectile.addDistanceTravelled(distanceTravelled);
					weaponProjectile.setLocation(thrownProjectile.getVelocity());
				}
			});

			timer.start(true);
		}
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

		return new RepeatingTimer(plugin, 1, t -> {
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

	private Vector applySpread(Vector originalVector, double spreadFactor) {
		double offsetX = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetY = (random.nextDouble() - 0.5) * spreadFactor;
		double offsetZ = (random.nextDouble() - 0.5) * spreadFactor;

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}
}
