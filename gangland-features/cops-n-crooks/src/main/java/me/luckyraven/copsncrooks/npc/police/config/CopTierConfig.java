package me.luckyraven.copsncrooks.npc.police.config;

import me.luckyraven.copsncrooks.entity.npc.NpcDifficulty;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Represents a single cop tier with its associated loadout and stats.
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
		NpcDifficulty difficulty
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
