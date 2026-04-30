package org.luckyraven.gangland.inventory.flow;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.inventory.InventoryHandler;

/**
 * A single screen inside a {@link MultiPanelInventory} flow. Panels own their own size + title (Bukkit forbids resizing
 * an open inventory, so switching between panels of different sizes causes the host to open a fresh
 * {@link InventoryHandler}). The same {@link FlowSession} instance is threaded through every panel.
 */
public interface Panel<S extends FlowSession> {

	int size(S session);

	String title(S session);

	void render(MultiPanelInventory<S> host, InventoryHandler handler, Player viewer, S session);

}
