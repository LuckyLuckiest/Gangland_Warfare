package me.luckyraven.weapon.repair.effects;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.repair.api.RepairEffect;
import me.luckyraven.weapon.repair.item.RepairItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Restores weapon durability.
 */
public class DurabilityRestoreEffect implements RepairEffect {

	@Override
	public void apply(@NotNull Weapon weapon, @NotNull RepairItem repairItem, @NotNull Player player,
					  @Nullable ConfigurationSection config) {
		int amount = 10; // Default amount

		if (config != null && config.contains("amount")) {
			amount = config.getInt("amount", 10);
		}

		// Calculate restoration amount
		int currentDurability = weapon.getCurrentDurability();
		int maxDurability     = weapon.getDurability();
		int newDurability     = Math.min(maxDurability, currentDurability + amount);

		weapon.setCurrentDurability((short) newDurability);

		// Send feedback to player
		if (newDurability >= maxDurability) {
			player.sendMessage("§a✔ Weapon fully repaired!");
		} else {
			player.sendMessage(String.format("§a✔ Restored %d durability (%d/%d)",
											 amount, newDurability, maxDurability));
		}
	}

	@Override
	public @NotNull String getEffectType() {
		return "durability_restore";
	}

	@Override
	public boolean canApply(@NotNull Weapon weapon, @Nullable ConfigurationSection config) {
		// Can only apply if weapon is damaged
		return weapon.getCurrentDurability() < weapon.getDurability();
	}

	@Override
	public @NotNull String getDescription(@Nullable ConfigurationSection config) {
		int amount = config != null ? config.getInt("amount", 10) : 10;
		return String.format("Restores %d durability", amount);
	}
}