package me.luckyraven.gadget.listener.car;

import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.vehicle.ParkedVehicle;
import me.luckyraven.gadget.car.vehicle.VehicleSession;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.types.melee.MeleeWeapon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles damage to car vehicles (both active sessions and parked cars).
 * <ul>
 *   <li><b>Player left-click</b> ({@link VehicleDamageEvent}) — Bukkit fires this instead of
 *       {@link EntityDamageEvent} when a player punches a non-living vehicle entity. Damage is
 *       taken from the player's held melee weapon; falls back to the raw event damage.</li>
 *   <li><b>Shift + left-click on a parked car</b> — picks it up as an item.</li>
 *   <li><b>Weapon projectile hit</b> ({@link EntityDamageEvent}) — {@link me.luckyraven.weapon.listener.projectile.ProjectileDamageListener}
 *       sets the weapon's configured damage on the event before this handler runs.</li>
 *   <li><b>Any other source</b> (explosion, fire, etc.) — applies the raw event damage.</li>
 * </ul>
 */
@ListenerHandler
@AutowireTarget({CarService.class, WeaponService.class})
public class CarDamageListener implements Listener {

	private final CarService    carService;
	private final WeaponService weaponService;

	public CarDamageListener(CarService carService, WeaponService weaponService) {
		this.carService    = carService;
		this.weaponService = weaponService;
	}

	/**
	 * Handles direct player punches on the Minecart. {@link EntityDamageEvent} does not fire for Minecarts on player
	 * attack; {@link VehicleDamageEvent} is the correct hook for this case.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onVehicleDamage(VehicleDamageEvent event) {
		if (!(event.getVehicle() instanceof Minecart)) return;
		// Projectile and explosion sources are handled via EntityDamageEvent
		if (!(event.getAttacker() instanceof Player player)) return;

		UUID entityUUID = event.getVehicle().getUniqueId();

		VehicleSession session = carService.getVehicleRegistry().getByEntity(entityUUID);
		ParkedVehicle  parked  = carService.getParkedVehicle(entityUUID);
		if (session == null && parked == null) return;

		event.setCancelled(true);

		// Shift + left-click on a parked car → pick it up
		if (parked != null && player.isSneaking()) {
			carService.pickupCar(player, entityUUID);
			return;
		}

		int damage = resolveMeleeDamage(player, Math.max(1, (int) Math.ceil(event.getDamage())));

		if (session != null) {
			session.damage(damage);
			if (session.isDestroyed()) {
				carService.destroyCar(entityUUID, false);
			}
		} else {
			carService.damageParkedCar(entityUUID, damage);
		}
	}

	/**
	 * Handles weapon projectile hits and all other non-melee damage sources (explosions, fire, etc.). For weapon
	 * projectiles, {@link me.luckyraven.weapon.listener.projectile.ProjectileDamageListener} writes the correct weapon
	 * damage onto the event at LOWEST priority before this runs at NORMAL.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity     = event.getEntity();
		UUID   entityUUID = entity.getUniqueId();

		// --- actively driven vehicle ---
		VehicleSession session = carService.getVehicleRegistry().getByEntity(entityUUID);
		if (session != null) {
			event.setCancelled(true);
			int damage = (int) Math.ceil(event.getDamage());
			session.damage(damage);
			if (session.isDestroyed()) {
				carService.destroyCar(entityUUID, false);
			}
			return;
		}

		// --- parked vehicle ---
		ParkedVehicle parked = carService.getParkedVehicle(entityUUID);
		if (parked == null) return;

		event.setCancelled(true);
		int damage = (int) Math.ceil(event.getDamage());
		carService.damageParkedCar(entityUUID, damage);
	}

	/**
	 * Returns the melee weapon's configured damage if the player is holding one, otherwise returns {@code fallback}
	 * (vanilla punch damage).
	 */
	private int resolveMeleeDamage(Player player, int fallback) {
		ItemStack item   = player.getInventory().getItemInMainHand();
		var       weapon = weaponService.validateAndGetWeapon(player, item);
		if (weapon instanceof MeleeWeapon melee) {
			return (int) Math.ceil(melee.getMeleeData().getDamage());
		}
		return fallback;
	}
}
