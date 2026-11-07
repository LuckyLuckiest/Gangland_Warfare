package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;

import java.util.ArrayList;
import java.util.List;

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
public final class TurfSelectionResolver {

	private TurfSelectionResolver() {
	}

	/**
	 * The turf whose id is the word {@code raw} the admin typed. A word that is not a number and an id with no turf both
	 * tell {@code sender} {@code TURF_NOT_FOUND} with the word as typed and return {@code null}.
	 */
	static @Nullable Turf byId(CommandSender sender, TurfManager turfs, TurfMessageContract messages, String raw) {
		int id;
		try {
			id = Integer.parseInt(raw);
		} catch (NumberFormatException exception) {
			messages.send(sender, "TURF_NOT_FOUND", "turf", raw);
			return null;
		}
		Turf turf = turfs.get(id);
		if (turf == null) {
			messages.send(sender, "TURF_NOT_FOUND", "turf", raw);
			return null;
		}
		return turf;
	}

	/** Every turf id as a tab completion, in manager order, in a list the caller may change. */
	static List<String> idCompletions(TurfManager turfs) {
		List<String> ids = new ArrayList<>();
		for (Turf turf : turfs.getAll()) {
			ids.add(String.valueOf(turf.getId()));
		}
		return ids;
	}

	public static @Nullable Turf resolve(CommandSender sender,
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
