package org.luckyraven.gangland.sign.extension;

import org.bukkit.entity.Player;

/**
 * A module-owned branch of the {@code view} sign's item lookup. The core cannot name a module type, so a module
 * that wants {@code /glw-view <its own item name>} to open a custom inventory registers a bean implementing this
 * contract; {@link org.luckyraven.gangland.sign.aspect.ViewInventoryAspect} tries every provider before falling
 * back to the generic item view.
 */
public interface SignViewProvider {

	/**
	 * Open a view for {@code content} if this provider owns that name.
	 *
	 * @return {@code true} when the view was opened, {@code false} to let the next provider (and finally the
	 *         core's generic item view) try.
	 */
	boolean open(Player player, String content);
}
