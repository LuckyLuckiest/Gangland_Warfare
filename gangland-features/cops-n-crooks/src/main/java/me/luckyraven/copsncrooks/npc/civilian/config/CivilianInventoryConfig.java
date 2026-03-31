package me.luckyraven.copsncrooks.npc.civilian.config;

import java.util.Map;

/**
 * Trader inventory configuration for a civilian NPC type.
 * <p>
 * When present on a type, right-clicking the NPC opens a chest GUI with the specified items.
 *
 * @param title color-coded inventory title (translated via ChatUtil)
 * @param size number of inventory slots (must be a multiple of 9, max 54)
 * @param slotItems map of slot index → raw ItemParser string
 */
public record CivilianInventoryConfig(String title, int size, Map<Integer, String> slotItems) {
}
