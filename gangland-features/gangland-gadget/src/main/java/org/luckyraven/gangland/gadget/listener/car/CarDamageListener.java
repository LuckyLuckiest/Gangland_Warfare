package org.luckyraven.gangland.gadget.listener.car;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.car.message.CarMessageContract;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;

import java.util.UUID;

/**
 * Handles player-melee and environmental damage to car vehicles (active sessions and parked cars):
 *
 * <ul>
 *   <li>{@link VehicleDamageEvent} — player left-click. Bukkit fires this instead of
 *       {@code EntityDamageEvent} for vehicle entities. Uses melee weapon damage if held (via
 *       {@link CarMeleeWeaponLookup}, only when Bartizan is available); falls back to the raw punch value. Shift +
 *       left-click picks up a parked car.</li>
 *   <li>{@link EntityDamageEvent} at {@code NORMAL} — explosions, fire, and other non-projectile
 *       sources. Projectile cause is skipped here to avoid double-counting with the raytracer hook.</li>
 * </ul>
 *
 * <p>The Bartizan-event-typed hooks (weapon explosion / raytracer impact damage) live in
 * {@link CarWeaponDamageListener} instead (WS7 G5, B6) — kept out of this always-loaded class so it carries zero
 * Bartizan symbols and {@code module.yml} can drop the hard {@code Plugins: [Bartizan]} dependency.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({CarService.class, CarAccessPolicy.class, CarMessageContract.class, CarDamageState.class})
public class CarDamageListener implements Listener {

	private final CarService         carService;
	private final CarAccessPolicy    accessPolicy;
	private final CarMessageContract messages;
	private final CarDamageState     carDamageState;

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
		carDamageState.markPendingRightClickInteract(playerUUID);
		// Clean up after 1 tick in case VehicleDamageEvent never fires for this interact
		Bukkit.getScheduler()
		      .runTaskLater(carService.getPlugin(), () -> carDamageState.removePendingRightClickInteract(playerUUID), 1L);
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

		// T-KR3 (review M3): a thrown-grenade explosion damages a Minecart through VehicleDamageEvent (attacker =
		// the thrower), not EntityDamageEvent as onEntityDamage's own javadoc assumed — see
		// CarDamageState.recentWeaponExplosionDamage's javadoc for the full ordering. Without this check the
		// weapon-configured explosion damage applied by CarWeaponDamageListener.onWeaponEntityDamage was
		// immediately doubled here as an ordinary punch.
		if (carDamageState.consumeRecentWeaponExplosionDamage(entityUUID)) return;

		// Shift + left-click on a parked car → pick it up.
		// Guard: if VehicleDamageEvent was caused by a right-click interact (Paper quirk where cancelling
		// PlayerInteractEntityEvent falls back to an attack packet), suppress the pickup entirely.
		boolean holdingWeapon = Settings.isBartizanAvailable() && CarMeleeWeaponLookup.isHoldingWeapon(player);
		if (parked != null && player.isSneaking() && !holdingWeapon) {
			if (carDamageState.consumePendingRightClickInteract(player.getUniqueId())) return;

			// GD-06: pickup returns the car item, so an ungated pickup was outright theft.
			if (!accessPolicy.canUse(player, parked.getPlacerUUID())) {
				player.sendMessage(messages.noPermission());
				return;
			}

			carService.pickupCar(player, entityUUID);
			return;
		}

		int fallback = Math.max(1, (int) Math.ceil(event.getDamage()));
		int damage   = Settings.isBartizanAvailable() ? CarMeleeWeaponLookup.resolveMeleeDamage(player, fallback) :
				fallback;
		CarDamageMath.applyDamage(entityUUID, session, parked, damage, carService);
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

		// A weapon-caused explosion already applied its configured damage via
		// CarWeaponDamageListener.onWeaponEntityDamage, fired before ThrowableAction's World#createExplosion
		// produces this vanilla event for the same vehicle — skip to avoid double damage. A plain (non-weapon)
		// explosion never populates the set, so it still falls through below.
		if (carDamageState.consumeRecentWeaponExplosionDamage(entityUUID)) return;

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		CarDamageMath.applyDamage(entityUUID, session, parked, damage, carService);
	}
}
