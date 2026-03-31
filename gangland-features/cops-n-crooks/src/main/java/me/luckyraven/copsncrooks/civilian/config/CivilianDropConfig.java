package me.luckyraven.copsncrooks.civilian.config;

import java.util.List;

/**
 * Drop configuration for a civilian NPC type.
 *
 * @param itemEntries raw ItemParser strings (e.g. {@code "weapon:pistol"}, {@code "material:GOLD_INGOT"})
 * @param experience experience awarded to the killer via the levels system (not vanilla XP)
 */
public record CivilianDropConfig(List<String> itemEntries, double experience) {
}
