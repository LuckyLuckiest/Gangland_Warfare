package org.luckyraven.gangland.turf.npc;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

/**
 * Opens the per-turf Quartermaster panel for {@code viewer}. The implementation ({@code TurfPowerupOpenContractImpl})
 * lives in gangland-turf itself (where the turf module + gang module + economy are all visible) and constructs the
 * {@link MenuFlow} chain. Cops-n-crooks only holds this seam so the right-click listener stays free of turf-module
 * imports while still being able to drive the panel.
 *
 * <p>Owner-gang gating, no-owner / no-gang denial, and "turf was deleted" handling all live in the impl — this
 * interface just receives the request and is expected to either show a panel or send the viewer a deny chat.
 */
public interface TurfPowerupOpenContract {

	void open(Player viewer, int turfId);
}
