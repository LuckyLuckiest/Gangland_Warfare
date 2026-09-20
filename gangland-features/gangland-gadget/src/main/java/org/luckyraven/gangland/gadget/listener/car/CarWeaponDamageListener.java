package org.luckyraven.gangland.gadget.listener.car;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.bartizan.api.event.WeaponEntityDamageEvent;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;

import java.util.UUID;

/**
 * The two Bartizan-event-typed handlers extracted from {@code CarDamageListener} (B2/B6, WS7 G5): {@code
 * condition = "isBartizanAvailable"} means Keystone's listener scan never instantiates or reflects on this class
 * when Bartizan is absent — {@code module.yml} no longer fail-fasts on {@code Plugins: [Bartizan]}, so this is now
 * the only thing standing between an absent Bartizan and a {@code NoClassDefFoundError} on these two event types.
 * No {@code @Bean} method (B6) — auto-scanned and constructor-injected like {@code CarDamageListener} itself.
 */
@ListenerHandler(condition = "isBartizanAvailable")
@RequiredArgsConstructor
@AutowireTarget({CarService.class, CarDamageState.class})
public class CarWeaponDamageListener implements Listener {

	private final CarService     carService;
	private final CarDamageState carDamageState;

	@EventHandler(priority = EventPriority.NORMAL)
	public void onWeaponEntityDamage(WeaponEntityDamageEvent event) {
		UUID           entityUUID = event.getEntity().getUniqueId();
		VehicleSession session    = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked     = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		if (event.kind() == WeaponEntityDamageEvent.DamageKind.EXPLOSION) {
			carDamageState.markRecentWeaponExplosionDamage(entityUUID);
			Bukkit.getScheduler().runTaskLater(carService.getPlugin(),
			                                   () -> carDamageState.consumeRecentWeaponExplosionDamage(entityUUID), 1L);
		}

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		CarDamageMath.applyDamage(entityUUID, session, parked, damage, carService);
	}

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
		if (shooter != null && carDamageState.isPendingRightClickInteract(shooter.getUniqueId())) {
			return;
		}

		int damage = Math.max(1, (int) Math.ceil(event.getDamage()));
		CarDamageMath.applyDamage(entityUUID, session, parked, damage, carService);
	}
}
