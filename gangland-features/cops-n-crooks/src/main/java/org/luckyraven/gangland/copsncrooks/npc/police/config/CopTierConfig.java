package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;

/**
 * Represents a single cop tier with its associated loadout and stats.
 *
 * @param tactics this tier's squad-tactics tuning (formation arc, engagement), read over {@code Cops.Tactics}.
 * @param fireRateMultiplier this tier's gun cadence as a fraction of the weapon's own (player) fire rate
 * 		({@code Fire_Rate_Multiplier}); the cop's Keystone fire-rate scale is its inverse.
 */
public record CopTierConfig(
		int tier,
		String displayName,
		double health,
		double damage,
		double speed,
		double cuffRadius,
		boolean canUseWeapons,
		boolean skipCuffing,
		List<String> weaponNamePool,
		List<ItemStack> weaponPool,
		ItemStack helmet,
		ItemStack chestplate,
		ItemStack leggings,
		ItemStack boots,
		NpcDifficulty difficulty,
		TacticsConfig tactics,
		double fireRateMultiplier
) {

	/**
	 * Returns a complete equipment array ordered: helmet, chestplate, leggings, boots.
	 *
	 * @return the armor array
	 */
	public ItemStack[] getArmorContents() {
		return new ItemStack[]{helmet, chestplate, leggings, boots};
	}
}
