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
 * @param ignoreDarkness {@code true} = this tier ignores the darkness sight factor ({@code Ignore_Darkness}, 0.20.0).
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
		double fireRateMultiplier,
		boolean ignoreDarkness
) {

	/** The pre-0.20 arity: {@code ignoreDarkness} is false. Kept so older callers (and 0.21.0) compile unchanged. */
	public CopTierConfig(int tier, String displayName, double health, double damage, double speed, double cuffRadius,
	                     boolean canUseWeapons, boolean skipCuffing, List<String> weaponNamePool,
	                     List<ItemStack> weaponPool, ItemStack helmet, ItemStack chestplate, ItemStack leggings,
	                     ItemStack boots, NpcDifficulty difficulty, TacticsConfig tactics, double fireRateMultiplier) {
		this(tier, displayName, health, damage, speed, cuffRadius, canUseWeapons, skipCuffing, weaponNamePool,
		     weaponPool, helmet, chestplate, leggings, boots, difficulty, tactics, fireRateMultiplier, false);
	}

	/**
	 * Returns a complete equipment array ordered: helmet, chestplate, leggings, boots.
	 *
	 * @return the armor array
	 */
	public ItemStack[] getArmorContents() {
		return new ItemStack[]{helmet, chestplate, leggings, boots};
	}
}
