package me.luckyraven.weapon.repair.effects;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.repair.api.RepairEffect;
import me.luckyraven.weapon.repair.item.RepairItem;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixes broken mechanical components in a weapon. This effect repairs internal mechanisms and can restore significant
 * durability.
 */
public class ComponentFixEffect implements RepairEffect {

	@Override
	public void apply(@NotNull Weapon weapon, @NotNull RepairItem repairItem, @NotNull Player player,
					  @Nullable ConfigurationSection config) {
		// Restore a significant amount (20% of max durability)
		int repairAmount = (int) (weapon.getDurability() * 0.20);

		if (config != null && config.contains("amount")) {
			repairAmount = config.getInt("amount", repairAmount);
		}

		int currentDurability = weapon.getCurrentDurability();
		int maxDurability     = weapon.getDurability();
		int newDurability     = Math.min(maxDurability, currentDurability + repairAmount);

		weapon.setCurrentDurability((short) newDurability);

		// Play mechanical fixing sound
		player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);

		// Mark components as fixed
//		weapon.getState().put("components_fixed", true);
//		weapon.getState().put("last_component_fix", System.currentTimeMillis());

		// Could reset wear-and-tear effects
//		weapon.getState().remove("jamming_chance");
//		weapon.getState().remove("misfire_chance");

		player.sendMessage("§a✔ Mechanical components repaired! (+" + repairAmount + " durability)");
	}

	@Override
	public @NotNull String getEffectType() {
		return "component_fix";
	}

	@Override
	public boolean canApply(@NotNull Weapon weapon, @Nullable ConfigurationSection config) {
		// Can only apply if weapon is significantly damaged
		return weapon.getCurrentDurability() < (weapon.getDurability() * 0.75);
	}

	@Override
	public @NotNull String getDescription(@Nullable ConfigurationSection config) {
		return "Repairs mechanical components";
	}
}