package me.luckyraven.weapon.listener;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.events.WeaponProjectileHitEvent;
import me.luckyraven.weapon.events.WeaponProjectileLaunchEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
@AutowireTarget({WeaponService.class})
public class ProjectileDamage implements Listener {

	private final static Map<Integer, Weapon> weaponInstance = new ConcurrentHashMap<>();

	private final WeaponService weaponManager;

	private final Map<Integer, ProjectileEventQueue> eventQueues;

	public ProjectileDamage(WeaponService weaponService) {
		this.weaponManager = weaponService;
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

		int projectileId = projectile.getEntityId();
		var queue        = eventQueues.computeIfAbsent(projectileId, ProjectileEventQueue::new);

		// Add damage event to queue
		queue.addDamageEvent(event);

		// Try to process queue
		boolean isPlayer = event.getDamager() instanceof Player;
		tryProcessQueue(projectileId, isPlayer ? (Player) event.getDamager() : null);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onProjectileHit(ProjectileHitEvent event) {
		var projectile   = event.getEntity();
		int projectileId = projectile.getEntityId();

		var queue = eventQueues.computeIfAbsent(projectileId, ProjectileEventQueue::new);

		// Add hit event to queue
		queue.addHitEvent(event);

		// Try to process queue
		boolean isPlayer = event.getEntity().getShooter() instanceof Player;
		tryProcessQueue(projectileId, isPlayer ? (Player) event.getEntity().getShooter() : null);
	}

	private void tryProcessQueue(int projectileId, Player shooter) {
		ProjectileEventQueue queue = eventQueues.get(projectileId);

		if (queue != null && queue.isProcessed()) return;

		if (queue == null) {
			eventQueues.put(projectileId, queue = new ProjectileEventQueue(projectileId));
		}

		// Check if we have both events OR if the hit event happened without damage (block hit)
		boolean hasDamageEvent = queue.hasDamageEvent();
		boolean hasHitEvent    = queue.hasHitEvent();

		// If we have a hit event but no damage event, it is a block/miss hit
		if (hasHitEvent && !hasDamageEvent) {
			// Check if hit was on a block (not entity)
			if (queue.getHitEvent().getHitEntity() == null) {
				// Block hit only - process immediately
				executeQueue(projectileId, queue, shooter);
				return;
			}

			// Hit entity but no damage event yet - wait for damage event
			return;
		}

		// If we have a damage event but no hit event - wait for the hit event
		if (hasDamageEvent && !hasHitEvent) return;

		if (!hasDamageEvent) return;

		executeQueue(projectileId, queue, shooter);
	}

	private void executeQueue(int projectileId, ProjectileEventQueue queue, Player shooter) {
		if (queue.isProcessed()) return;
		queue.setProcessed(true);

		Weapon weapon = weaponInstance.get(projectileId);
		if (weapon == null) {
			cleanup(projectileId);
			return;
		}

		var newEvent = new WeaponProjectileHitEvent(weapon);
		Bukkit.getPluginManager().callEvent(newEvent);

		if (newEvent.isCancelled()) return;

		// Process damage events FIRST (in order they were added)
		for (EntityDamageByEntityEvent damageEvent : queue.getDamageEvents()) {
			processDamageEvent(damageEvent, weapon, shooter);
		}

		// Then process hit event
		ProjectileHitEvent hitEvent = queue.getHitEvent();
		if (hitEvent != null) {
			processHitEvent(hitEvent);
		}

		// Cleanup
		cleanup(projectileId);
	}

	private void processDamageEvent(EntityDamageByEntityEvent event, Weapon weapon, Player shooter) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return;
		if (!(event.getDamager() instanceof Projectile projectile)) return;

		Random random = new Random();

		boolean criticalHitOccurred = random.nextDouble() < weapon.getProjectileCriticalHitChance() / 100D;

		double damage = criticalHitOccurred ?
						weapon.getProjectileDamage() + weapon.getProjectileCriticalHitDamage() :
						weapon.getProjectileDamage();

		// set the damage
		event.setDamage(weaponManager.isHeadPosition(projectile.getLocation(), entity.getLocation()) ?
						damage + weapon.getProjectileHeadDamage() :
						damage);

		if (criticalHitOccurred && shooter != null) {
			var sound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, "ITEM_SHIELD_BREAK", 1F, 1F);

			sound.playSound(shooter);
		}

		// set the fire damage
		entity.setFireTicks(weapon.getProjectileFireTicks());

		entity.setNoDamageTicks(0);
	}

	private void processHitEvent(ProjectileHitEvent event) {
		Projectile projectile = event.getEntity();

		if (!projectile.isDead()) {
			projectile.remove();
		}

		// set the explosive damage
		explosiveProjectile(event);
	}

	private void explosiveProjectile(ProjectileHitEvent event) {
		Projectile projectile = event.getEntity();

		if (!(projectile instanceof Fireball fireball)) return;

		ProjectileSource shooter = fireball.getShooter();

		if (!(shooter instanceof LivingEntity)) return;

		Entity   targetEntity = event.getHitEntity();
		Location hitEntity    = targetEntity != null ? targetEntity.getLocation() : fireball.getLocation();

		Block    hitBlock = event.getHitBlock();
		Location hitLoc   = hitBlock != null ? hitBlock.getLocation() : hitEntity;

		// damage nearby entities
		int    entityId        = projectile.getEntityId();
		Weapon weapon          = weaponInstance.get(entityId);
		double explosionRadius = weapon.getProjectileExplosionDamage();

		for (Entity entity : fireball.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
			if (!(entity instanceof LivingEntity target && entity != shooter)) continue;

			double distance = target.getLocation().distance(hitLoc);
			double damage   = 20 * (1 - (distance / explosionRadius));

			target.damage(Math.max(damage, 0D), fireball);
		}
	}

	private void cleanup(int projectileId) {
		weaponInstance.remove(projectileId);
		eventQueues.remove(projectileId);
	}

	@Getter
	private static class ProjectileEventQueue {
		private final int                             projectileId;
		private final List<EntityDamageByEntityEvent> damageEvents;

		private ProjectileHitEvent hitEvent;
		@Setter
		private boolean            processed;

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
