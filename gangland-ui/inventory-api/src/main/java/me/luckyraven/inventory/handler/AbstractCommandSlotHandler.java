package me.luckyraven.inventory.handler;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

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
	public Slot handle(SlotContext ctx, InventoryOpener opener) {
		ItemBuilder item = SlotItemFactory.create(ctx.item(), ctx.itemName(), ctx.data(), ctx.lore(), ctx.enchanted());
		Slot        slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		ConfigurationSection configurationSection = ctx.eventSection();

		if (configurationSection == null) return slot;

		String command    = configurationSection.getString("Command");
		String inventory  = configurationSection.getString("Inventory");
		String permission = configurationSection.getString("Permission");

		slot.setClickable((player, inv, builder) -> {
			if (permission != null && !player.hasPermission(permission)) return;
			if (command != null) player.performCommand(stripSlash(command));
			if (inventory != null) opener.openInventory(player, inventory);
			onSlotAction(player, inv, builder);
		});

		return slot;
	}

	/**
	 * Called after the command / inventory action. Override to add event-specific behavior.
	 */
	protected void onSlotAction(Player player, InventoryHandler inv, ItemBuilder builder) { }

}
