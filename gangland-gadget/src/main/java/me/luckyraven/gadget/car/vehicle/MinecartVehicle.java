package me.luckyraven.gadget.car.vehicle;

import me.luckyraven.gadget.car.Car;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Vehicle implementation backed by a {@link Minecart} entity. Movement is applied via velocity manipulation rather than
 * teleportation. Does not require rails.
 */
public class MinecartVehicle implements VehicleEntity {

	private final Car car;

	private Minecart minecart;

	public MinecartVehicle(Car car) {
		this.car = car;
	}

	/**
	 * Wraps an already-existing minecart entity (used when re-loading parked vehicles after restart).
	 */
	public MinecartVehicle(Car car, Minecart existingMinecart) {
		this.car      = car;
		this.minecart = existingMinecart;
	}

	@Override
	public void spawn(Location location) {
		World world = location.getWorld();
		if (world == null) return;

		minecart = world.spawn(location.clone(), Minecart.class, cart -> {
			// Use a high internal max-speed ceiling so our manually applied velocity is never capped.
			// We manage speed entirely via setVelocity each tick.
			cart.setMaxSpeed(10.0);
			cart.setSlowWhenEmpty(false);
		});
	}

	@Override
	public void mount(Player player) {
		if (minecart != null && !minecart.isDead()) {
			minecart.addPassenger(player);
		}
	}

	@Override
	public void despawn() {
		if (minecart != null && !minecart.isDead()) {
			minecart.eject();
			minecart.remove();
		}
	}

	@Override
	public void updateMovement(double speed, float yaw) {
		if (minecart == null || minecart.isDead()) return;

		double radians = Math.toRadians(yaw);
		double dx      = -Math.sin(radians) * speed;
		double dz      = Math.cos(radians) * speed;

		// Always set velocity to override minecart physics drag each tick.
		// Negative speed naturally produces reverse movement along the same yaw axis.
		minecart.setVelocity(new Vector(dx, minecart.getVelocity().getY(), dz));
	}

	@Override
	public boolean isAlive() {
		return minecart != null && !minecart.isDead() && minecart.isValid();
	}

	@Override
	public Location getLocation() {
		return minecart != null ? minecart.getLocation() : null;
	}

	@Override
	public Entity getBukkitEntity() {
		return minecart;
	}

	@Override
	public UUID getEntityUUID() {
		return minecart != null ? minecart.getUniqueId() : null;
	}
}
