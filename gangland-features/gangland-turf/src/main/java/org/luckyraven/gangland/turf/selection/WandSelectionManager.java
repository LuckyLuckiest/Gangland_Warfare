package org.luckyraven.gangland.turf.selection;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active per-admin wand selection. Selections are in-memory only and cleared on quit.
 */
public final class WandSelectionManager {

	public static final String WAND_NBT_KEY     = "gangturf_wand";
	public static final String ADMIN_PERMISSION = "gangland.turf.admin";

	private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

	public @NotNull Selection get(Player player) {
		return selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
	}

	public void clear(UUID playerId) {
		selections.remove(playerId);
	}
}
