package me.luckyraven.listener.player.weapon;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.WeaponProjectileLaunchEvent;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class ProjectileDamage implements Listener {

	private final static Map<Integer, Weapon> weaponInstance = new ConcurrentHashMap<>();

	private final WeaponManager                      weaponManager;
	private final Map<Integer, ProjectileEventQueue> eventQueues;

	public ProjectileDamage(Gangland gangland) {
		this.weaponManager = gangland.getInitializer().getWeaponManager();
		this.eventQueues   = new ConcurrentHashMap<>();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileLaunch(WeaponProjectileLaunchEvent event) {
		if (!(event.getProjectile().getShooter() instanceof Player)) return;

		Weapon weapon = event.getWeapon();

		if (weapon == null) return;

		int entityId = event.getProjectile().getEntityId();

		weaponInstance.put(entityId, weapon);
		eventQueues.put(entityId, new ProjectileEventQueue(entityId));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onProjectileEntityDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return;
		if (!(event.getDamager() instanceof Projectile projectile)) return;
		if (entity instanceof ItemFrame || entity instanceof ArmorStand) return;

		int                  projectileId = projectile.getEntityId();
		ProjectileEventQueue queue        = eventQueues.get(projectileId);

		if (queue == null) return;

		// Add damage event to queue
		queue.addDamageEvent(event);

		// Try to process queue
		tryProcessQueue(projectileId);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onProjectileHit(ProjectileHitEvent event) {
		Projectile           projectile   = event.getEntity();
		int                  projectileId = projectile.getEntityId();
		ProjectileEventQueue queue        = eventQueues.get(projectileId);

		if (queue == null) return;

		// Add hit event to queue
		queue.addHitEvent(event);

		// Try to process queue
		tryProcessQueue(projectileId);
	}

	private void tryProcessQueue(int projectileId) {
		ProjectileEventQueue queue = eventQueues.get(projectileId);
		if (queue == null || queue.isProcessed()) return;

		// Check if we have both events OR if hit event happened without damage (block hit)
		boolean hasDamageEvent = queue.hasDamageEvent();
		boolean hasHitEvent    = queue.hasHitEvent();

		// If we have hit event but no damage event, it's a block/miss hit
		if (hasHitEvent && !hasDamageEvent) {
			// Check if hit was on a block (not entity)
			if (queue.getHitEvent().getHitEntity() == null) {
				// Block hit only - process immediately
				executeQueue(projectileId, queue);
				return;
			}
			// Hit entity but no damage event yet - wait for damage event
			return;
		}

		// If we have damage event but no hit event - wait for hit event
		if (hasDamageEvent && !hasHitEvent) {
			return;
		}

		// We have both events - process them in order
		if (hasDamageEvent && hasHitEvent) {
			executeQueue(projectileId, queue);
		}
	}

	private void executeQueue(int projectileId, ProjectileEventQueue queue) {
		if (queue.isProcessed()) return;
		queue.setProcessed(true);

		Weapon weapon = weaponInstance.get(projectileId);
		if (weapon == null) {
			cleanup(projectileId);
			return;
		}

		// Process damage events FIRST (in order they were added)
		for (EntityDamageByEntityEvent damageEvent : queue.getDamageEvents()) {
			processDamageEvent(damageEvent, weapon);
		}

		// Then process hit event
		ProjectileHitEvent hitEvent = queue.getHitEvent();
		if (hitEvent != null) {
			processHitEvent(hitEvent, projectileId);
		}

		// Cleanup
		cleanup(projectileId);
	}

	private void processDamageEvent(EntityDamageByEntityEvent event, Weapon weapon) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return;
		if (!(event.getDamager() instanceof Projectile projectile)) return;

		Random random = new Random();
		double damage = random.nextDouble() < weapon.getProjectileCriticalHitChance() / 100D ?
						weapon.getProjectileDamage() + weapon.getProjectileCriticalHitDamage() :
						weapon.getProjectileDamage();

		// set the damage
		event.setDamage(weaponManager.isHeadPosition(projectile.getLocation(), entity.getLocation()) ?
						damage + weapon.getProjectileHeadDamage() :
						damage);

		// set the fire damage
		entity.setFireTicks(weapon.getProjectileFireTicks());
	}

	private void processHitEvent(ProjectileHitEvent event, int projectileId) {
		Projectile projectile = event.getEntity();

		if (!projectile.isDead()) {
			projectile.remove();
		}

		// set the explosive damage
		explosiveProjectile(event);
	}

	private void explosiveProjectile(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof Fireball fireball)) return;
		if (!(fireball.getShooter() instanceof LivingEntity)) return;

		Location hitEntity = event.getHitEntity() != null ? event.getHitEntity().getLocation() : fireball.getLocation();
		Location hitLoc    = event.getHitBlock() != null ? event.getHitBlock().getLocation() : hitEntity;

		// damage nearby entities
		int    entityId        = event.getEntity().getEntityId();
		Weapon weapon          = weaponInstance.get(entityId);
		double explosionRadius = weapon.getProjectileExplosionDamage();

		for (Entity entity : fireball.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
			if (!(entity instanceof LivingEntity target && entity != fireball.getShooter())) continue;

			double distance = target.getLocation().distance(hitLoc);
			double damage   = 20 * (1 - (distance / explosionRadius));

			target.damage(Math.max(damage, 0D), fireball);
		}
	}

	private void cleanup(int projectileId) {
		weaponInstance.remove(projectileId);
		eventQueues.remove(projectileId);
	}

	private static class ProjectileEventQueue {
		private final int                             projectileId;
		@Getter
		private final List<EntityDamageByEntityEvent> damageEvents;
		@Getter
		private       ProjectileHitEvent              hitEvent;
		@Setter
		@Getter
		private       boolean                         processed;

		public ProjectileEventQueue(int projectileId) {
			this.projectileId = projectileId;
			this.damageEvents = new ArrayList<>();
			this.processed    = false;
		}

		public void addDamageEvent(EntityDamageByEntityEvent event) {
			damageEvents.add(event);
		}

		public void addHitEvent(ProjectileHitEvent event) {
			this.hitEvent = event;
		}

		public boolean hasDamageEvent() {
			return !damageEvents.isEmpty();
		}

		public boolean hasHitEvent() {
			return hitEvent != null;
		}

	}

}
