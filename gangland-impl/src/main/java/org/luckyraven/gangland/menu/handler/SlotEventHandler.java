package org.luckyraven.gangland.menu.handler;

import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.gangland.menu.part.Slot;

/**
 * Contract for building a {@link Slot} from a YAML slot-event config section.
 *
 * <p>Implementations receive a {@link MenuOpener} (WS2 G3: replaces the old {@code InventoryOpener}) instead of a
 * direct reference to the plugin, so the built {@code ClickHandler}s can open another named menu without depending
 * on a concrete menu-opening implementation.
 */
public interface SlotEventHandler {

	Slot handle(SlotContext context, MenuOpener opener);

}
