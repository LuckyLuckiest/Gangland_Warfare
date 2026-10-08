package org.luckyraven.gangland.copsncrooks.npc.police.targeting;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.wanted.Wanted;

import java.util.UUID;

/**
 * Manages the resolution of wanted players as targets for cop NPCs.
 */
public interface TargetingManager {

	/**
	 * Registers a player as wanted.
	 *
	 * @param player the wanted player
	 * @param wanted the wanted data
	 */
	void registerWanted(Player player, Wanted wanted);

	/**
	 * Removes a player from the wanted registry.
	 *
	 * @param playerId the player UUID
	 */
	void unregisterWanted(UUID playerId);

	/**
	 * Returns whether the given player is currently wanted.
	 *
	 * @param playerId the player UUID
	 *
	 * @return true if the player is wanted
	 */
	boolean isWanted(UUID playerId);

	/**
	 * Whether the player is on the post-escape search (0.16.1 T-187): cops still hunt him, at zero stars, and never cuff or
	 * shoot at him. Only a targeting manager that runs the search answers yes.
	 *
	 * @param playerId the player UUID
	 *
	 * @return true while the player is searched for after an escape
	 */
	default boolean isSearching(UUID playerId) {
		return false;
	}

	/**
	 * Returns the wanted level for a given player.
	 *
	 * @param playerId the player UUID
	 *
	 * @return the wanted level, or 0 if not wanted
	 */
	int getWantedLevel(UUID playerId);

	/**
	 * Finds the nearest wanted player to the given location, or null if none exist.
	 *
	 * @param from the reference player (cop's current target context)
	 *
	 * @return the best target player, or null
	 */
	@Nullable Player findBestTarget(Player from);
}