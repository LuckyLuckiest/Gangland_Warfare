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
 * Cleans a weapon, removing dirt and grime. This could restore a small amount of durability and improve weapon
 * performance.
 */
public class CleaningEffect implements RepairEffect {

	@Override
	public void apply(@NotNull Weapon weapon, @NotNull RepairItem repairItem, @NotNull Player player,
					  @Nullable ConfigurationSection config) {
		// Restore a small amount of durability (5% of max)
		int cleaningAmount = (int) (weapon.getDurability() * 0.05);

		if (config != null && config.contains("amount")) {
			cleaningAmount = config.getInt("amount", cleaningAmount);
		}

		int currentDurability = weapon.getCurrentDurability();
		int maxDurability     = weapon.getDurability();
		int newDurability     = Math.min(maxDurability, currentDurability + cleaningAmount);

		weapon.setCurrentDurability((short) newDurability);

		// Play cleaning sound
		player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.2f);

		// Update weapon state (could store "cleanliness" in weapon state map)
//		weapon.getState().put("cleaned", true);
//		weapon.getState().put("last_cleaned", System.currentTimeMillis());

		player.sendMessage("§a✔ Weapon cleaned! (+" + cleaningAmount + " durability)");
	}

	@Override
	public @NotNull String getEffectType() {
		return "cleaning";
	}

	@Override
	public @NotNull String getDescription(@Nullable ConfigurationSection config) {
		return "Cleans the weapon and restores condition";
	}
}