package me.luckyraven.inventory.handler;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.inventory.part.Slot;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Handles {@code OnClick} and {@code OnInteract} slot events.
 *
 * <p>Also handles the right-click-only case (when {@link SlotContext#eventSection()} is
 * {@code null} but {@link SlotContext#rightClickSection()} is present).
 */
public class ClickSlotHandler implements SlotEventHandler {

	private static String stripSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}

	@Override
	public Slot handle(SlotContext ctx, InventoryOpener opener) {
		ItemBuilder item = SlotItemFactory.create(ctx.itemResolver(), ctx.item(), ctx.itemName(), ctx.data(),
		                                          ctx.lore(), ctx.enchanted());

		// Right-click-only: no left-click event section was found in the YAML
		if (ctx.eventSection() == null) {
			return buildRightClickOnly(ctx, item, opener);
		}

		Slot slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		applyLeftClick(slot, ctx.eventSection(), opener);

		if (ctx.rightClickSection() != null) {
			applyRightClick(slot, ctx.rightClickSection(), opener);
		}

		return slot;
	}

	private Slot buildRightClickOnly(SlotContext ctx, ItemBuilder item, InventoryOpener opener) {
		Slot slot = new Slot(ctx.slotLoc(), true, ctx.draggable(), item);

		// Empty left-click so the slot is still registered as clickable
		slot.setClickable((player, inv, builder) -> { });

		if (ctx.rightClickSection() != null) {
			applyRightClick(slot, ctx.rightClickSection(), opener);
		}

		return slot;
	}

	private void applyLeftClick(Slot slot, ConfigurationSection section, InventoryOpener opener) {
		String command    = section.getString("Command");
		String inventory  = section.getString("Inventory");
		String permission = section.getString("Permission");

		slot.setClickable((player, inv, builder) -> {
			if (permission != null && !player.hasPermission(permission)) return;

			if (command != null) {
				player.performCommand(stripSlash(command));
			}

			if (inventory != null) {
				opener.openInventory(player, inventory);
			}
		});
	}

	private void applyRightClick(Slot slot, ConfigurationSection section, InventoryOpener opener) {
		String command    = section.getString("Command");
		String inventory  = section.getString("Inventory");
		String permission = section.getString("Permission");

		slot.setRightClickable((player, inv, builder) -> {
			if (permission != null && !player.hasPermission(permission)) return;

			if (command != null) {
				player.performCommand(stripSlash(command));
			}

			if (inventory != null) {
				opener.openInventory(player, inventory);
			}
		});
	}
}
