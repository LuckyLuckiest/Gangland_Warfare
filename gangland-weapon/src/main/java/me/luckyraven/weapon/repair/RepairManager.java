package me.luckyraven.weapon.repair;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.repair.api.RepairEffect;
import me.luckyraven.weapon.repair.effects.CleaningEffect;
import me.luckyraven.weapon.repair.effects.ComponentFixEffect;
import me.luckyraven.weapon.repair.effects.DurabilityRestoreEffect;
import me.luckyraven.weapon.repair.item.RepairItem;
import me.luckyraven.weapon.repair.item.RepairItemManager;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Central manager for the weapon repair system. Coordinates repair items, effects, and stations.
 */
public class RepairManager {

	private final JavaPlugin                plugin;
	private final RepairItemManager         repairItemManager;
	private final Map<String, RepairEffect> registeredEffects;

	public RepairManager(@NotNull JavaPlugin plugin) {
		this.plugin            = plugin;
		this.repairItemManager = new RepairItemManager(plugin);
		this.registeredEffects = new HashMap<>();
	}

	/**
	 * Initializes the repair system.
	 */
	public void initialize() {
		// Register default effects
		registerDefaultEffects();

		// Initialize repair item manager
		repairItemManager.initialize();
	}

	/**
	 * Registers a repair effect.
	 *
	 * @param id The effect type ID
	 * @param effect The effect implementation
	 */
	public void registerRepairEffect(@NotNull String id, @NotNull RepairEffect effect) {
		registeredEffects.put(id.toLowerCase(), effect);
	}

	/**
	 * Gets a repair effect by ID.
	 *
	 * @param id The effect type ID
	 *
	 * @return The effect, or null if not found
	 */
	@Nullable
	public RepairEffect getRepairEffect(@NotNull String id) {
		return registeredEffects.get(id.toLowerCase());
	}

	/**
	 * Applies a repair to a weapon using a repair item.
	 *
	 * @param player The player performing the repair
	 * @param weapon The weapon to repair
	 * @param repairItem The repair item to use
	 *
	 * @return true if the repair was successful
	 */
	public boolean applyRepair(@NotNull Player player, @NotNull Weapon weapon, @NotNull ItemStack repairItem) {
		// Validate inputs
		if (weapon.isBroken()) {
			player.sendMessage("§c✘ This weapon is completely broken and cannot be repaired!");
			return false;
		}

		// Get repair item data
		RepairItem repairItemData = repairItemManager.getRepairItem(repairItem);
		if (repairItemData == null) {
			player.sendMessage("§c✘ Invalid repair item!");
			return false;
		}

		// Check if repair item can be used
		if (!RepairItem.canUse(repairItem)) {
			player.sendMessage("§c✘ This repair item is broken!");
			return false;
		}

		// Check if weapon needs repair
		if (weapon.getCurrentDurability() >= weapon.getDurability()) {
			player.sendMessage("§c✘ This weapon doesn't need repairing!");
			return false;
		}

		// Apply all effects
		boolean anyEffectApplied = false;
		for (ConfigurationSection effectConfig : repairItemData.getEffects()) {
			String effectType = effectConfig.getString("type");
			if (effectType == null) {
				continue;
			}

			RepairEffect effect = getRepairEffect(effectType);
			if (effect == null) {
				plugin.getLogger().warning("Unknown repair effect: " + effectType);
				continue;
			}

			// Check if effect can be applied
			if (!effect.canApply(weapon, effectConfig)) {
				continue;
			}

			// Apply effect
			try {
				effect.apply(weapon, repairItemData, player, effectConfig);
				anyEffectApplied = true;
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Error applying repair effect: " + effectType, e);
			}
		}

		if (!anyEffectApplied) {
			player.sendMessage("§c✘ No repair effects could be applied!");
			return false;
		}

		// Play success sound
		player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

		return true;
	}

	/**
	 * Gets the repair item manager.
	 *
	 * @return The repair item manager
	 */
	@NotNull
	public RepairItemManager getRepairItemManager() {
		return repairItemManager;
	}

	/**
	 * Reloads the repair system.
	 */
	public void reload() {
		plugin.getLogger().info("Reloading weapon repair system...");
		repairItemManager.reload();
		plugin.getLogger().info("Weapon repair system reloaded!");
	}

	/**
	 * Gets all registered repair effects.
	 *
	 * @return A map of effect ID to RepairEffect
	 */
	@NotNull
	public Map<String, RepairEffect> getAllRepairEffects() {
		return new HashMap<>(registeredEffects);
	}

	/**
	 * Registers default repair effects.
	 */
	private void registerDefaultEffects() {
		registerRepairEffect("durability_restore", new DurabilityRestoreEffect());
		registerRepairEffect("cleaning", new CleaningEffect());
		registerRepairEffect("component_fix", new ComponentFixEffect());

		plugin.getLogger().info("Registered " + registeredEffects.size() + " repair effects");
	}
}