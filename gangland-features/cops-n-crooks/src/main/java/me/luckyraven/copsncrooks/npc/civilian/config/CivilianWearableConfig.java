package me.luckyraven.copsncrooks.npc.civilian.config;

/**
 * Armor-slot configuration for a civilian NPC type.
 * <p>
 * Each field is a raw ItemParser string (e.g. {@code "LEATHER_CHESTPLATE"} or {@code "wearable:riot_armor"}). An empty
 * string means the slot is left empty.
 */
public record CivilianWearableConfig(String helmet, String chestplate, String leggings, String boots) {
}
