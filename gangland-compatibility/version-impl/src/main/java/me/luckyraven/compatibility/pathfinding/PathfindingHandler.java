package me.luckyraven.compatibility.pathfinding;

import org.bukkit.Location;
import org.bukkit.entity.Mob;

import java.util.UUID;

/**
 * Interface for NMS pathfinding operations. Implementations handle version-specific NMS code.
 */
public interface PathfindingHandler {

	/**
	 * Makes the mob navigate to the target location.
	 *
	 * @param mob the mob to move
	 * @param target the target location
	 * @param speed movement speed multiplier (1.0 = normal)
	 *
	 * @return true if path was successfully set
	 */
	boolean navigateTo(Mob mob, Location target, double speed);

	/**
	 * Stops the mob's current navigation.
	 *
	 * @param mob the mob to stop
	 */
	void stopNavigation(Mob mob);

	/**
	 * Checks if the mob is currently navigating.
	 *
	 * @param mob the mob to check
	 *
	 * @return true if actively navigating
	 */
	boolean isNavigating(Mob mob);

	/**
	 * Gets the current path target location, if any.
	 *
	 * @param mob the mob to check
	 *
	 * @return target location or null if not navigating
	 */
	Location getTargetLocation(Mob mob);

	/**
	 * Clears all AI goals from the mob (for custom AI control).
	 *
	 * @param mob the mob to clear goals from
	 */
	void clearAIGoals(Mob mob);

	/**
	 * Sets whether the mob should have vanilla AI enabled.
	 *
	 * @param mob the mob
	 * @param enabled whether AI is enabled
	 */
	void setAIEnabled(Mob mob, boolean enabled);

	/**
	 * Cleans up any cached data for the entity. Call when entity is removed/despawned.
	 *
	 * @param entityId the entity UUID
	 */
	default void cleanup(UUID entityId) {
		// Default no-op, NMS implementations may override
	}
}
