package me.luckyraven.weapon.repair.api;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.repair.item.RepairItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single repair effect that can be applied to a weapon.
 * <p>
 * Effects are modular and can be registered to the repair system. Each effect is configured via YAML and applied when a
 * repair item is used.
 */
public interface RepairEffect {

	/**
	 * Applies this effect to the weapon.
	 *
	 * @param weapon The weapon being repaired
	 * @param repairItem The repair item being consumed
	 * @param player The player performing the repair
	 * @param config The effect's configuration section (contains custom parameters)
	 */
	void apply(@NotNull Weapon weapon, @NotNull RepairItem repairItem, @NotNull Player player,
			   @Nullable ConfigurationSection config);

	/**
	 * Gets the unique identifier for this effect type.
	 *
	 * @return The effect type ID (e.g., "durability_restore")
	 */
	@NotNull
	String getEffectType();

	/**
	 * Validates whether this effect can be applied to the given weapon.
	 *
	 * @param weapon The weapon to validate
	 * @param config The effect's configuration
	 *
	 * @return true if the effect can be applied, false otherwise
	 */
	default boolean canApply(@NotNull Weapon weapon, @Nullable ConfigurationSection config) {
		return true;
	}

	/**
	 * Gets a description of what this effect does.
	 *
	 * @param config The effect's configuration
	 *
	 * @return A human-readable description
	 */
	@NotNull
	default String getDescription(@Nullable ConfigurationSection config) {
		return getEffectType();
	}
}