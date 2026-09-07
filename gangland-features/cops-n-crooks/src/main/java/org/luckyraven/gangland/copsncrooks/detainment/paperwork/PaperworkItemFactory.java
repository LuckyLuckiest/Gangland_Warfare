package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import org.bukkit.inventory.ItemStack;

/**
 * Builds the "Jail Paperwork" item given to a player on jail intake, and tests whether an arbitrary {@link ItemStack}
 * is an instance of it (used to strip on release and to route right-click interactions to the paperwork GUI).
 */
public interface PaperworkItemFactory {

	ItemStack create();

	boolean isPaperwork(ItemStack item);
}
