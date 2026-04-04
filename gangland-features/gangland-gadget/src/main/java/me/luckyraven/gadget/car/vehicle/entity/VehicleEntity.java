package me.luckyraven.gadget.car.vehicle.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Abstraction over the underlying Bukkit entity used to represent a drivable car in the world.
 */
public interface VehicleEntity {

	/**
	 * Spawns the vehicle entity at the given location. The location's yaw is used as the initial facing direction. Does
	 * not mount any passenger — call {@link #mount(Player)} separately.
	 */
	void spawn(Location location);

	/**
	 * Adds the given player as a passenger of this vehicle.
	 */
	void mount(Player player);

	/**
	 * Removes the vehicle entity from the world.
	 */
	void despawn();

	/**
	 * Moves the vehicle entity according to the given speed and yaw direction.
	 *
	 * @param speed current speed in blocks/tick
	 * @param yaw the direction to move in (degrees)
	 */
	void updateMovement(double speed, float yaw);

	/**
	 * Forces the entity to face the given yaw direction without changing its position. Used during burnout where
	 * velocity is near-zero and the heading must be set explicitly.
	 *
	 * @param yaw heading in degrees
	 */
	void setFacing(float yaw);

	/**
	 * Returns {@code true} if the underlying entity is still alive and valid.
	 */
	boolean isAlive();

	/**
	 * Returns the current world location of the vehicle entity.
	 */
	Location getLocation();

	/**
	 * Returns the underlying Bukkit entity.
	 */
	Entity getBukkitEntity();

	/**
	 * Returns the UUID of the underlying Bukkit entity.
	 */
	UUID getEntityUUID();

	/**
	 * Plays a brief side-to-side wobble animation on the vehicle entity by applying a sine-wave velocity impulse for a
	 * short duration. No-op if the entity does not support it.
	 *
	 * @param plugin the plugin instance used to schedule the animation task
	 */
	default void wobble(JavaPlugin plugin) {
	}
}
