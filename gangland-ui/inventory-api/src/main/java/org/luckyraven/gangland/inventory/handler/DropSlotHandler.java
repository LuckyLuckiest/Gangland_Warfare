package org.luckyraven.gangland.inventory.handler;

/**
 * Handles {@code OnDrop} (PlayerDropItemEvent) slot events.
 *
 * <p>Triggers the configured command / inventory-open when the player presses Q on this slot
 * inside the custom GUI. Detection is performed by a dedicated Bukkit listener that checks
 * {@code InventoryAction.DROP_ONE_SLOT} / {@code DROP_ALL_SLOT} on the bound inventory.
 */
public class DropSlotHandler extends AbstractCommandSlotHandler { }
