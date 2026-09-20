package org.luckyraven.gangland.gadget.listener.car;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.gangland.gadget.car.CarService;
import org.luckyraven.gangland.gadget.car.vehicle.ParkedVehicle;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;

import java.util.UUID;

/**
 * WS7 G5 fix round 1 (review M2): the {@code applyDamage} body was byte-identical between {@code
 * CarDamageListener} and {@code CarWeaponDamageListener} (a deliberate ponytail call in G5's 4-file budget) until a
 * 5th class (the Bartizan-reference allowlist test) made the duplication no longer the smallest diff.
 * Extracted verbatim, unchanged apart from {@code carService} becoming an explicit parameter since this is no
 * longer an instance method of either listener. Carries zero Bartizan symbols, same as the two call sites that used
 * to hold this body directly — must never appear in the Bartizan-reference allowlist.
 */
final class CarDamageMath {

	private CarDamageMath() {
	}

	static void applyDamage(UUID entityUUID, VehicleSession session, ParkedVehicle parked, int damage,
	                         CarService carService) {
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
}
