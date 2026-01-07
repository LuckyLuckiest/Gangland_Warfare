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
import me.luckyraven.weapon.modifiers.BlockBreakModifier;
import me.luckyraven.weapon.modifiers.ModifierHandler;
import me.luckyraven.weapon.projectile.BlockDamageManager;
import me.luckyraven.weapon.projectile.ProjectileState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
@AutowireTarget({WeaponService.class})
public class ProjectileDamage implements Listener {

	private final static Map<Integer, Weapon>          weaponInstance   = new ConcurrentHashMap<>();
	private final static Map<Integer, ProjectileState> projectileStates = new ConcurrentHashMap<>();

	private final WeaponService      weaponManager;
	private final BlockDamageManager blockDamageManager;

	private final Map<Integer, ProjectileEventQueue> eventQueues;

	public ProjectileDamage(WeaponService weaponService, BlockDamageManager blockDamageManager) {
		this.weaponManager      = weaponService;
		this.blockDamageManager = blockDamageManager;
		this.eventQueues        = new ConcurrentHashMap<>();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileLaunch(WeaponProjectileLaunchEvent event) {
		if (!(event.getProjectile().getShooter() instanceof Player player)) return;

		Weapon weapon = event.getWeapon();

		if (weapon == null) return;

		int entityId = event.getProjectile().getEntityId();

		weaponInstance.put(entityId, weapon);
		projectileStates.put(entityId, new ProjectileState(weapon));
		eventQueues.put(entityId, new ProjectileEventQueue(entityId));

		// Handle tracer particles on launch
		if (weapon.getModifiersData().hasTracer()) {
			Location start = event.getProjectile().getLocation();
			Vector vector = event.getProjectile()
								 .getVelocity()
								 .normalize()
								 .multiply(weapon.getProjectileData().getDistance());
			Location end = start.clone().add(vector);
			ModifierHandler.spawnTracerParticles(weapon, start, end, player);
		}
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

		ProjectileState state = projectileStates.get(projectileId);

		var newEvent = new WeaponProjectileHitEvent(weapon);
		Bukkit.getPluginManager().callEvent(newEvent);

		if (newEvent.isCancelled()) return;

		// Process damage events FIRST (in order they were added)
		boolean shouldContinue = true;
		for (EntityDamageByEntityEvent damageEvent : queue.getDamageEvents()) {
			shouldContinue = processDamageEvent(damageEvent, weapon, shooter, state);
		}

		// Then process hit event
		ProjectileHitEvent hitEvent = queue.getHitEvent();
		if (hitEvent != null) {
			processHitEvent(hitEvent, weapon, state, shouldContinue);
		}

		// Cleanup only if projectile should not continue
		if (!shouldContinue || (hitEvent != null && hitEvent.getEntity().isDead())) {
			cleanup(projectileId);
		}
	}

	private boolean processDamageEvent(EntityDamageByEntityEvent event, Weapon weapon, Player shooter,
									   ProjectileState state) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return false;
		if (!(event.getDamager() instanceof Projectile projectile)) return false;

		var random         = new Random();
		var projectileData = weapon.getProjectileData();
		var damageData     = weapon.getDamageData();

		boolean criticalHitOccurred = random.nextDouble() < damageData.getCriticalHitChance() / 100D;

		// Use state's current damage (accounts for penetration/ricochet reductions)
		double baseDamage = state != null ? state.getCurrentDamage() : projectileData.getDamage();

		double damage = criticalHitOccurred ? baseDamage + damageData.getCriticalHitDamage() : baseDamage;

		// Apply armor piercing modifier
		damage = ModifierHandler.calculateArmorPiercingDamage(damage, entity, weapon);

		// Apply head damage bonus
		boolean isHeadshot = weaponManager.isHeadPosition(projectile.getLocation(), entity.getLocation());
		if (isHeadshot) {
			damage += damageData.getHeadDamage();
		}

		event.setDamage(damage);

		if (criticalHitOccurred && shooter != null) {
			var sound = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, "ITEM_SHIELD_BREAK", 1F, 1F);
			sound.playSound(shooter);
		}

		// set the fire damage
		entity.setFireTicks(damageData.getFireTicks());
		entity.setNoDamageTicks(0);

		// Handle entity penetration
		return state != null && ModifierHandler.handleEntityPenetration(state, projectile);
	}

	private void processHitEvent(ProjectileHitEvent event, Weapon weapon, ProjectileState state,
								 boolean continueFromDamage) {
		Projectile projectile   = event.getEntity();
		Block      hitBlock     = event.getHitBlock();
		BlockFace  hitBlockFace = event.getHitBlockFace();

		// Handle ricochet first
		if (hitBlock != null && state != null && hitBlockFace != null) {
			if (ModifierHandler.handleRicochet(state, projectile, hitBlock, hitBlockFace)) {
				// Projectile ricocheted, don't remove it
				// Reset the event queue for this projectile to handle next hit
				int projectileId = projectile.getEntityId();
				eventQueues.put(projectileId, new ProjectileEventQueue(projectileId));
				return;
			}

			// Handle block penetration
			if (ModifierHandler.handleBlockPenetration(state, projectile, hitBlock)) {
				// Projectile penetrated, let it continue
				int projectileId = projectile.getEntityId();
				eventQueues.put(projectileId, new ProjectileEventQueue(projectileId));
				return;
			}
		}

		// Handle block hit with break modifiers
		if (hitBlock != null) {
			handleBlockHit(hitBlock, weapon);
		}

		// Remove projectile if it didn't ricochet or penetrate
		if (!projectile.isDead() && !continueFromDamage) {
			projectile.remove();
		}

		// set the explosive damage
		explosiveProjectile(event);
	}

	private void handleBlockHit(Block block, Weapon weapon) {
		List<BlockBreakModifier> modifiers = weapon.getModifiersData().getBreakBlocks();

		for (BlockBreakModifier modifier : modifiers) {
			if (!modifier.appliesTo(block.getType())) continue;

			blockDamageManager.applyDamage(block, modifier);
			break;
		}
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
		double explosionRadius = weapon.getDamageData().getExplosionDamage();

		for (Entity entity : fireball.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
			if (!(entity instanceof LivingEntity target && entity != shooter)) continue;

			double distance = target.getLocation().distance(hitLoc);
			double damage   = 20 * (1 - (distance / explosionRadius));

			target.damage(Math.max(damage, 0D), fireball);
		}
	}

	private void cleanup(int projectileId) {
		weaponInstance.remove(projectileId);
		projectileStates.remove(projectileId);
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
