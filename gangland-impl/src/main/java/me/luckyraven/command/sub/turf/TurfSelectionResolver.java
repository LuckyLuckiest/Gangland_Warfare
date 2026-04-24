package me.luckyraven.command.sub.turf;

import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.Selection;
import me.luckyraven.turf.selection.WandSelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the turf a /glw turf command should operate on without ever asking the admin for an id.
 * <p>
 * Order:
 * <ol>
 *   <li>{@link Selection#getActiveTurfId()} — last created / teleported-to / explicitly-selected turf.</li>
 *   <li>{@link TurfManager#findAt(org.bukkit.Location)} — the turf the admin is currently standing inside.</li>
 *   <li>Sends {@code TURF_NO_ACTIVE} via the message contract and returns {@code null}.</li>
 * </ol>
 * When a turf is resolved by standing-in, it is promoted to the admin's active selection so later commands stay
 * consistent with the first resolution.
 */
final class TurfSelectionResolver {

	private TurfSelectionResolver() {
	}

	static @Nullable Turf resolve(CommandSender sender,
	                              TurfManager turfs,
	                              WandSelectionManager selections,
	                              TurfMessageContract messages) {
		if (!(sender instanceof Player player)) {
			messages.send(sender, "TURF_NO_ACTIVE");
			return null;
		}
		Selection selection = selections.get(player);
		Integer   activeId  = selection.getActiveTurfId();
		if (activeId != null) {
			Turf cached = turfs.get(activeId);
			if (cached != null) {
				return cached;
			}
			// Stale selection (turf was deleted) — clear it and fall back.
			selection.setActiveTurfId(null);
		}
		Turf standing = turfs.findAt(player.getLocation());
		if (standing != null) {
			selection.setActiveTurfId(standing.getId());
			return standing;
		}
		messages.send(sender, "TURF_NO_ACTIVE");
		return null;
	}
}
