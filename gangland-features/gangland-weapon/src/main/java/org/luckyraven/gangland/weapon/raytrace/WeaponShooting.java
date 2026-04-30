package org.luckyraven.gangland.weapon.raytrace;

import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.weapon.dto.ProjectileData;
import org.luckyraven.gangland.weapon.projectile.ProjectileState;
import org.luckyraven.gangland.weapon.projectile.ProjectileType;
import org.luckyraven.gangland.weapon.types.gun.GunWeapon;

/**
 * Shared dispatch helper that fires a {@link GunWeapon} through the unified weapon raytracer, regardless of whether the
 * shooter is a {@link org.bukkit.entity.Player} or an NPC. Routes BULLET / SPREAD through
 * {@link WeaponRaytracer#fireInstant} and ROCKET / FLARE through a {@link SteppedProjectileTask} that drives a cosmetic
 * Bukkit projectile entity.
 * <p>
 * Both {@code GunAction} (player firing path) and {@code AbstractNpc#performGanglandWeaponAttack} (NPC firing path)
 * call this helper so the dispatch logic stays in one place. Player-specific concerns (durability decrement, recoil,
 * sound to shooter, item update) live in the callers.
 */
public final class WeaponShooting {

	/**
	 * Default pellet count for SPREAD weapons. Mirrors the hardcoded value in legacy {@code Spread}.
	 */
	public static final int SPREAD_PELLET_COUNT = 8;

	private WeaponShooting() {
	}

	/**
	 * Fires the given gun weapon from the given shooter through the unified raytracer.
	 */
	public static void fire(JavaPlugin plugin, WeaponRaytracer raytracer, LivingEntity shooter, GunWeapon weapon) {
		ProjectileData projectileData = weapon.getProjectileData();
		ProjectileType type           = projectileData.getType();

		switch (type) {
			case BULLET, SPREAD -> fireHitscan(raytracer, shooter, weapon, projectileData, type);
			case ROCKET, FLARE -> fireSlow(plugin, raytracer, shooter, weapon, projectileData, type);
		}
	}

	private static void fireHitscan(WeaponRaytracer raytracer, LivingEntity shooter, GunWeapon weapon,
	                                ProjectileData projectileData, ProjectileType type) {
		int      pelletCount = type == ProjectileType.SPREAD ? SPREAD_PELLET_COUNT : 1;
		Vector   aimDir      = shooter.getEyeLocation().getDirection();
		Location origin      = shooter.getEyeLocation();
		double   distance    = projectileData.getDistance();
		double   baseDamage  = projectileData.getDamage();

		for (int i = 0; i < pelletCount; i++) {
			Vector pelletDir = weapon.getSpread().applySpread(aimDir.clone()).normalize();

			RaytraceRequest request = RaytraceRequest.builder()
			                                         .shooter(shooter)
			                                         .weapon(weapon)
			                                         .origin(origin.clone())
			                                         .direction(pelletDir)
			                                         .maxDistance(distance)
			                                         .baseDamage(baseDamage)
			                                         .gravity(projectileData.getGravity())
			                                         .projectileSpeed(projectileData.getSpeed())
			                                         .build();

			raytracer.fireInstant(request);
		}
	}

	private static void fireSlow(JavaPlugin plugin, WeaponRaytracer raytracer, LivingEntity shooter, GunWeapon weapon,
	                             ProjectileData projectileData, ProjectileType type) {
		Vector   aimDir         = shooter.getEyeLocation().getDirection();
		Location muzzle         = WeaponMuzzle.compute(shooter, aimDir);
		Vector   spreadDir      = weapon.getSpread().applySpread(aimDir).normalize();
		Vector   launchVelocity = spreadDir.clone().multiply(projectileData.getSpeed());

		Class<? extends Projectile> visualClass = type == ProjectileType.ROCKET ? Fireball.class : Firework.class;
		Projectile visual = raytracer.getVisualSpawner()
		                             .spawnCosmetic(visualClass, shooter, muzzle, launchVelocity);
		if (visual == null) {
			return;
		}

		RaytraceRequest request = RaytraceRequest.builder()
		                                         .shooter(shooter)
		                                         .weapon(weapon)
		                                         .origin(shooter.getEyeLocation())
		                                         .direction(spreadDir)
		                                         .maxDistance(projectileData.getDistance())
		                                         .baseDamage(projectileData.getDamage())
		                                         .build();

		ProjectileState state = new ProjectileState(weapon);
		RaytraceContext ctx   = new RaytraceContext(request, state);

		double speedPerTick = Math.max(0.1, projectileData.getSpeed());
		int    maxTicks     = (int) Math.ceil(projectileData.getDistance() / speedPerTick) * 2 + 20;

		boolean explode         = type == ProjectileType.ROCKET;
		double  explosionRadius = explode ? weapon.getDamageData().getExplosionDamage() : 0;

		new SteppedProjectileTask(plugin, raytracer, raytracer.getVisualSpawner(), visual, ctx, explode,
		                          explosionRadius, maxTicks).start();
	}

}
