package me.luckyraven.inventory.handler;

import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.inventory.part.Slot;

/**
 * Contract for building a {@link Slot} from a YAML slot-event config section.
 *
 * <p>Implementations live in {@code inventory-api} and receive an {@link InventoryOpener}
 * instead of a direct reference to the plugin — keeping them decoupled from {@code gangland-impl}.
 */
public interface SlotEventHandler {

	Slot handle(SlotContext context, InventoryOpener opener);

}
