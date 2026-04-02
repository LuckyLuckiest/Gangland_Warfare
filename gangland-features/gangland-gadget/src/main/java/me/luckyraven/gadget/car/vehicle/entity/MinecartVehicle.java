package me.luckyraven.gadget.car.vehicle.entity;

import me.luckyraven.gadget.car.Car;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
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

	@Override
	public void wobble(JavaPlugin plugin) {
		if (minecart == null || minecart.isDead() || !minecart.isEmpty()) return;

		Minecart cart  = minecart;
		Location start = cart.getLocation().clone();
		// Perpendicular (right) vector relative to the cart's facing direction
		Vector facing = start.getDirection().setY(0).normalize();
		Vector right  = new Vector(-facing.getZ(), 0, facing.getX());

		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (cart.isDead() || ticks >= 12) {
					cart.setVelocity(new Vector(0, 0, 0));
					cancel();
					return;
				}

				// Alternating direction each tick with decaying amplitude — set, not add
				double amplitude = 0.12 * (1.0 - (double) ticks / 12.0);
				double side      = ticks % 2 == 0 ? amplitude : -amplitude;

				cart.setVelocity(right.clone().multiply(side));
				ticks++;
			}
		}.runTaskTimer(plugin, 0L, 1L);
	}
}
