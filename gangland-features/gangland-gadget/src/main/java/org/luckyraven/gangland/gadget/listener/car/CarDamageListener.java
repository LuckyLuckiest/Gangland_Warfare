package org.luckyraven.gangland.gadget.listener.car;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.event.WeaponEntityDamageEvent;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.bartizan.api.weapon.MeleeWeapon;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.WeaponCatalog;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all damage to car vehicles (active sessions and parked cars) across three event hooks:
 *
 * <ul>
 *   <li>{@link VehicleDamageEvent} — player left-click. Bukkit fires this instead of
 *       {@code EntityDamageEvent} for vehicle entities. Uses melee weapon damage if held;
 *       falls back to the raw punch value. Shift + left-click picks up a parked car.</li>
 *   <li>{@link WeaponRaytraceImpactEvent} — fired by the unified weapon raytracer for any gun /
 *       slow projectile / incendiary / biological / throwable shot. Replaces the old
 *       {@code ProjectileHitEvent}-driven path; the legacy projectile lookup via
 *       {@code ProjectileDamageListener#getDamageForProjectile} has been removed.</li>
 *   <li>{@link EntityDamageEvent} at {@code NORMAL} — explosions, fire, and other non-projectile
 *       sources. Projectile cause is skipped here to avoid double-counting with the hook above.</li>
 * </ul>
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({CarService.class, CarAccessPolicy.class, CarMessageContract.class})
public class CarDamageListener implements Listener {

	private final CarService         carService;
	private final CarAccessPolicy    accessPolicy;
	private final CarMessageContract messages;

	/**
	 * Tracks players who have just right-clicked a car entity (populated by {@link #onCarRightClick} at {@code LOWEST}
	 * priority). Used in {@link #onVehicleDamage} to suppress a pickup when Paper fires {@code VehicleDamageEvent} as a
	 * fallback for a cancelled right-click interact.
	 */
	private final Set<UUID> pendingRightClickInteract = ConcurrentHashMap.newKeySet();

	/**
	 * Tracks vehicle entity UUIDs {@link #onWeaponEntityDamage} just applied weapon-configured explosion damage to.
	 * {@code ThrowableAction}'s explosion still calls {@code World#createExplosion}, which fires a vanilla
	 * {@link EntityDamageEvent}({@code ENTITY_EXPLOSION}/{@code BLOCK_EXPLOSION}) for the same vehicle right after —
	 * {@link #onEntityDamage} consults this set to skip that vanilla event instead of double-applying damage. A plain
	 * (non-weapon) explosion never populates this set, so it still falls through to the vanilla damage value.
	 */
	private final Set<UUID> recentWeaponExplosionDamage = ConcurrentHashMap.newKeySet();

	// ------------------------------------------------------------------
	// Right-click guard (prevents VehicleDamageEvent pickup false-positives)
	// ------------------------------------------------------------------

	/**
	 * Marks the player as having right-clicked a car entity this tick. Runs at {@code LOWEST} so it fires before any
	 * other listener (including {@code WeaponInteract}) can cancel the event. The flag is consumed by
	 * {@link #onVehicleDamage} to suppress a spurious pickup when Paper fires {@code VehicleDamageEvent} as a fallback
	 * after a cancelled right-click interact packet.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onCarRightClick(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		UUID entityUUID = event.getRightClicked().getUniqueId();
		if (!carService.isParkedVehicle(entityUUID) && carService.getVehicleRegistry().getByEntity(entityUUID) == null)
			return;
		UUID playerUUID = event.getPlayer().getUniqueId();
		pendingRightClickInteract.add(playerUUID);
		// Clean up after 1 tick in case VehicleDamageEvent never fires for this interact
		Bukkit.getScheduler()
		      .runTaskLater(carService.getPlugin(), () -> pendingRightClickInteract.remove(playerUUID), 1L);
	}

	// ------------------------------------------------------------------
	// Player melee (left-click)
	// ------------------------------------------------------------------

	/**
	 * {@code EntityDamageEvent} does not fire when a player punches a Minecart; Bukkit fires {@code VehicleDamageEvent}
	 * instead. This handler applies melee weapon damage or vanilla punch damage. Shift + left-click while the car is
	 * parked picks it up.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onVehicleDamage(VehicleDamageEvent event) {
		if (!(event.getVehicle() instanceof Minecart)) return;
		// Projectile/explosion sources produce their own events; only handle direct player punches
		if (!(event.getAttacker() instanceof Player player)) return;

		UUID           entityUUID = event.getVehicle().getUniqueId();
		VehicleSession session    = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked     = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		event.setCancelled(true);

		// Shift + left-click on a parked car → pick it up.
		// Guard: if VehicleDamageEvent was caused by a right-click interact (Paper quirk where cancelling
		// PlayerInteractEntityEvent falls back to an attack packet), suppress the pickup entirely.
		WeaponCatalog weapons = weapons();
		if (parked != null && player.isSneaking() &&
		    (weapons == null || !weapons.isWeapon(player.getInventory().getItemInMainHand()))) {
			if (pendingRightClickInteract.remove(player.getUniqueId())) return;

			// GD-06: pickup returns the car item, so an ungated pickup was outright theft.
			if (!accessPolicy.canUse(player, parked.getPlacerUUID())) {
				player.sendMessage(messages.noPermission());
				return;
			}

			carService.pickupCar(player, entityUUID);
			return;
		}

		int damage = resolveMeleeDamage(player, Math.max(1, (int) Math.ceil(event.getDamage())));
		applyDamage(entityUUID, session, parked, damage);
	}

	// ------------------------------------------------------------------
	// Explosions, fire, and other environmental sources
	// ------------------------------------------------------------------

	/**
	 * Handles explosions, fire, suffocation, and similar non-melee non-projectile damage. Projectile cause is
	 * deliberately skipped — weapon projectiles are handled in {@link #onProjectileHit} to guarantee the correct weapon
	 * damage value.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) return;

		Entity         entity     = event.getEntity();
		UUID           entityUUID = entity.getUniqueId();
		VehicleSession session    = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked     = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		event.setCancelled(true);

		// A weapon-caused explosion already applied its configured damage via onWeaponEntityDamage, fired before
		// ThrowableAction's World#createExplosion produces this vanilla event for the same vehicle — skip to avoid
		// double damage. A plain (non-weapon) explosion never populates the set, so it still falls through below.
		if (recentWeaponExplosionDamage.remove(entityUUID)) return;

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		applyDamage(entityUUID, session, parked, damage);
	}

	// ------------------------------------------------------------------
	// Incendiary and biological weapons
	// ------------------------------------------------------------------

	@EventHandler(priority = EventPriority.NORMAL)
	public void onWeaponEntityDamage(WeaponEntityDamageEvent event) {
		UUID           entityUUID = event.getEntity().getUniqueId();
		VehicleSession session    = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked     = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		if (event.kind() == WeaponEntityDamageEvent.DamageKind.EXPLOSION) {
			// See recentWeaponExplosionDamage's javadoc: the vanilla EntityDamageEvent createExplosion() fires
			// right after this event, for the same entity, must be skipped rather than double-applied.
			recentWeaponExplosionDamage.add(entityUUID);
			Bukkit.getScheduler()
			      .runTaskLater(carService.getPlugin(), () -> recentWeaponExplosionDamage.remove(entityUUID), 1L);
		}

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		applyDamage(entityUUID, session, parked, damage);
	}

	/**
	 * Handles vehicle hits from the unified weapon raytracer. Vehicles are not {@code LivingEntity} so
	 * {@code WeaponRaytracer} skips its default damage pipeline for them — this handler is the canonical hook for
	 * routing weapon damage onto a car.
	 * <p>
	 * Suppresses damage for one tick after the shooter right-clicks the same kind of vehicle so the player can enter a
	 * car they are aiming at without accidentally shooting it. The existing {@link #pendingRightClickInteract} flag
	 * (set by {@link #onCarRightClick}) tracks this state.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onWeaponRaytraceImpact(WeaponRaytraceImpactEvent event) {
		Entity hit = event.getHitEntity();
		if (hit == null) return;

		UUID           entityUUID = hit.getUniqueId();
		VehicleSession session    = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked     = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		LivingEntity shooter = event.getShooter();

		// Never damage the vehicle the shooter is currently riding — friendly fire on your own ride.
		if (shooter != null && hit.equals(shooter.getVehicle())) {
			return;
		}

		// If the shooter is mid-vehicle-entry (right-clicked a car within the last tick), suppress
		// the shot rather than damaging the car they're trying to get into.
		if (shooter != null && pendingRightClickInteract.contains(shooter.getUniqueId())) {
			return;
		}

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		applyDamage(entityUUID, session, parked, damage);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private void applyDamage(UUID entityUUID, VehicleSession session, ParkedVehicle parked, int damage) {
		if (session != null) {
			session.damage(damage);
			session.getEntity().wobble(carService.getPlugin());
			if (session.isDestroyed()) {
				Entity entity = session.getEntity().getBukkitEntity();
				if (entity != null) {
					ParticleUtil.spawnExplosionBurst(entity.getLocation());
				}
				carService.destroyCar(entityUUID, false);
			}
		} else if (parked != null) {
			boolean  willDestroy = parked.getDurability() <= damage;
			Location explodeLoc  = null;
			if (willDestroy) {
				Entity entity = parked.getEntity().getBukkitEntity();
				if (entity != null) {
					explodeLoc = entity.getLocation();
				}
			}
			carService.damageParkedCar(entityUUID, damage);
			parked.getEntity().wobble(carService.getPlugin());
			if (explodeLoc != null) {
				ParticleUtil.spawnExplosionBurst(explodeLoc);
			}
		}
	}

	/**
	 * Returns the melee weapon's configured damage if the player is holding one, otherwise the {@code fallback} value
	 * (vanilla punch damage).
	 */
	private int resolveMeleeDamage(Player player, int fallback) {
		WeaponCatalog weapons = weapons();
		if (weapons == null) return fallback;

		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weapons.validateAndGetWeapon(player, item);
		if (weapon instanceof MeleeWeapon melee) {
			return (int) Math.ceil(melee.getMeleeData().getDamage());
		}
		return fallback;
	}

	/**
	 * Resolves Bartizan's {@link WeaponCatalog} lazily, never cached in a field — Bartizan may enable after this
	 * module (or not be installed at all, per {@code module.yml}'s {@code Plugins: [Bartizan]}).
	 */
	@Nullable
	private WeaponCatalog weapons() {
		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		return rsp == null ? null : rsp.getProvider().weapons();
	}
}
