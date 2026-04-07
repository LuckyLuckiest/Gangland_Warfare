package me.luckyraven.weapon.listener.projectile;

import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.raytrace.WeaponVisualSpawner;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Vestigial listener that exists only to suppress the legacy {@code ProjectileHitEvent} /
 * {@code EntityDamageByEntityEvent} chain for cosmetic visual projectiles spawned by the new {@code WeaponRaytracer}.
 * All weapon damage logic now runs through the unified raytracer; this class only guards against vanilla event handling
 * for the visual carrier entities (Fireball, Firework, Snowball, etc.) so they don't double-process or trigger spurious
 * cops-n-crooks NPC reactions on contact.
 */
@ListenerHandler
@AutowireTarget({WeaponService.class})
public class ProjectileDamageListener implements Listener {

	private final WeaponVisualSpawner visualSpawner;

	public ProjectileDamageListener(WeaponVisualSpawner visualSpawner) {
		this.visualSpawner = visualSpawner;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onProjectileEntityDamage(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Projectile projectile)) return;

		// Cosmetic visuals do not drive damage — cancel the event so no downstream listener
		// (including cops-n-crooks NPC AI) reacts to a purely visual projectile hitting an entity.
		if (visualSpawner.isCosmetic(projectile.getEntityId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onProjectileHit(ProjectileHitEvent event) {
		int projectileId = event.getEntity().getEntityId();

		if (visualSpawner.isCosmetic(projectileId)) {
			visualSpawner.unregisterCosmetic(projectileId);
		}
	}

}
