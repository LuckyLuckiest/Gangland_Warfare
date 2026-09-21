package org.luckyraven.gangland.menu.handler;

import org.luckyraven.keystone.inventory.click.ClickContext;

/**
 * Handles {@code OnClose} slot events — a "close button" that runs the configured command then closes the player's
 * current inventory.
 */
public class CloseSlotHandler extends AbstractCommandSlotHandler {

	@Override
	protected void onSlotAction(ClickContext ctx) {
		ctx.closeMenu();
	}

}
