package me.luckyraven.weapon.raytrace;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns purely cosmetic Bukkit projectile entities (visual carriers) for weapons that want a visible flying object —
 * slow rockets, flares, throwables, and tracer-style hitscan visuals.
 * <p>
 * Cosmetic entities never drive damage logic. Their entity IDs are registered in {@link #cosmeticEntityIds}, and
 * {@code ProjectileDamageListener} early-returns out of {@code ProjectileHitEvent} and
 * {@code EntityDamageByEntityEvent} for any projectile in this set. Hit detection is owned exclusively by
 * {@link WeaponRaytracer}.
 */
public class WeaponVisualSpawner {

	private final Set<Integer> cosmeticEntityIds = ConcurrentHashMap.newKeySet();

	/**
	 * Marks the given entity as a cosmetic carrier so the legacy listener will ignore it.
	 */
	public void registerCosmetic(int entityId) {
		cosmeticEntityIds.add(entityId);
	}

	/**
	 * Removes the given entity from the cosmetic registry (called when the visual is despawned).
	 */
	public void unregisterCosmetic(int entityId) {
		cosmeticEntityIds.remove(entityId);
	}

	/**
	 * True if the given entity ID belongs to a registered cosmetic visual.
	 */
	public boolean isCosmetic(int entityId) {
		return cosmeticEntityIds.contains(entityId);
	}

	/**
	 * Spawns a Bukkit projectile of the given type at {@code spawnLocation} with the given velocity and registers it as
	 * cosmetic. The returned entity is silent, gravity-free, and shooter-owned; its only role is visual.
	 */
	public <T extends Projectile> T spawnCosmetic(Class<T> type, LivingEntity shooter, Location spawnLocation,
	                                              Vector velocity) {
		var world = spawnLocation.getWorld();
		if (world == null) {
			return null;
		}

		T projectile = world.spawn(spawnLocation, type);
		projectile.setSilent(true);
		projectile.setGravity(false);
		projectile.setShooter(shooter);
		projectile.setVelocity(velocity);

		registerCosmetic(projectile.getEntityId());
		return projectile;
	}

}
