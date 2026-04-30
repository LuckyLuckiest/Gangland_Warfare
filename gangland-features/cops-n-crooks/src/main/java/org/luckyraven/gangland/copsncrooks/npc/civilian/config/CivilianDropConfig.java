package org.luckyraven.gangland.copsncrooks.npc.civilian.config;

import java.util.List;

/**
 * Drop configuration for a civilian NPC type.
 *
 * @param itemEntries drop entries; each pairs an ItemParser string (e.g. {@code "weapon:pistol"},
 *        {@code "material:GOLD_INGOT"}) with a 0.0–1.0 roll chance
 * @param experience experience awarded to the killer via the levels system (not vanilla XP)
 */
public record CivilianDropConfig(List<DropEntry> itemEntries, double experience) {

	/**
	 * One drop-table entry. {@code chance} is rolled independently per death: {@code 1.0} always drops, {@code 0.0}
	 * never drops, {@code 0.3} drops ~30% of the time.
	 */
	public record DropEntry(String entry, double chance) {
	}
}
