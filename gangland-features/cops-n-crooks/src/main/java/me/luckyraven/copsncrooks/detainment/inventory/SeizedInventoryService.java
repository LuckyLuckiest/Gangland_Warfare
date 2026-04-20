package me.luckyraven.copsncrooks.detainment.inventory;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Snapshots a player's inventory on jail intake, restores it on legitimate release, and clears the snapshot when
 * consumed. Implementations are responsible for persistence.
 */
public interface SeizedInventoryService {

	/**
	 * Captures the player's current inventory (main + armour + offhand) and stores it. The caller is responsible for
	 * clearing the live inventory afterwards.
	 */
	void snapshot(Player player);

	/**
	 * Restores a previously captured snapshot into the player's inventory and clears the stored snapshot. No-op if no
	 * snapshot exists.
	 *
	 * @return {@code true} if a snapshot existed and was restored
	 */
	boolean restore(Player player);

	/**
	 * Returns whether a seized snapshot currently exists for this player.
	 */
	boolean has(UUID playerId);

	/**
	 * Discards any stored snapshot without restoring items (e.g. admin-chose to confiscate).
	 */
	void clear(UUID playerId);
}
