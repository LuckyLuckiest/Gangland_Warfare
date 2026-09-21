package org.luckyraven.gangland.menu.handler;

import org.bukkit.configuration.ConfigurationSection;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.menu.part.Slot;

/**
 * Base handler that reads {@code Command}, {@code Inventory}, and {@code Permission} from the event config section and
 * wires them as a left-click action.
 *
 * <p>Subclasses can override {@link #onSlotAction} to execute additional behavior after the
 * command / inventory-open has run (template-method pattern).
 */
public abstract class AbstractCommandSlotHandler implements SlotEventHandler {

	protected static String stripSlash(String cmd) {
		return cmd.startsWith("/") ? cmd.substring(1) : cmd;
	}

	@Override
	public Slot handle(SlotContext ctx, MenuOpener opener) {
		ItemBuilder item = SlotItemFactory.create(ctx.itemResolver(), ctx.item(), ctx.itemName(), ctx.data(),
		                                          ctx.lore(), ctx.enchanted());
		Slot slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		ConfigurationSection configurationSection = ctx.eventSection();

		if (configurationSection == null) return slot;

		String command    = configurationSection.getString("Command");
		String inventory  = configurationSection.getString("Inventory");
		String permission = configurationSection.getString("Permission");

		slot.setClickable(clickCtx -> {
			if (permission != null && !clickCtx.player().hasPermission(permission)) return;
			if (command != null) clickCtx.player().performCommand(stripSlash(command));
			if (inventory != null) opener.open(clickCtx.player(), inventory);
			onSlotAction(clickCtx);
		});

		return slot;
	}

	/**
	 * Called after the command / inventory action. Override to add event-specific behavior.
	 */
	protected void onSlotAction(ClickContext ctx) { }

}
