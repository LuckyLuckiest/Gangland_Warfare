package me.luckyraven.weapon.repair.item;

import lombok.Getter;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.repair.RepairKeys;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a repair item that can repair weapons.
 * <p>
 * Repair items have durability, levels, and multiple effects.
 */
@Getter
public class RepairItem {

	private final String                     id;
	private final String                     displayName;
	private final Material                   material;
	private final int                        level;
	private final int                        maxDurability;
	private final List<String>               lore;
	private final List<ConfigurationSection> effects;
	private final Map<String, Object>        metadata;
	private final int                        customModelData;

	public RepairItem(@NotNull String id, @NotNull String displayName, @NotNull Material material,
					  int level, int maxDurability, @NotNull List<String> lore,
					  @NotNull List<ConfigurationSection> effects, @NotNull Map<String, Object> metadata,
					  int customModelData) {
		this.id              = id;
		this.displayName     = displayName;
		this.material        = material;
		this.level           = level;
		this.maxDurability   = maxDurability;
		this.lore            = new ArrayList<>(lore);
		this.effects         = new ArrayList<>(effects);
		this.metadata        = metadata;
		this.customModelData = customModelData;
	}

	/**
	 * Checks if this repair item can be used (has durability remaining).
	 *
	 * @param item The item to check
	 *
	 * @return true if the item can be used
	 */
	public static boolean canUse(@NotNull ItemStack item) {
		ItemBuilder builder = new ItemBuilder(item);
		if (!builder.hasNBTTag(RepairKeys.REPAIR_ITEM_DURABILITY)) {
			return false;
		}
		return getDurability(item) > 0;
	}

	/**
	 * Gets the current durability of a repair item.
	 *
	 * @param item The item
	 *
	 * @return The current durability, or 0 if not a repair item
	 */
	public static int getDurability(@NotNull ItemStack item) {
		ItemBuilder builder = new ItemBuilder(item);
		if (!builder.hasNBTTag(RepairKeys.REPAIR_ITEM_DURABILITY)) {
			return 0;
		}
		return builder.getIntegerTagData(RepairKeys.REPAIR_ITEM_DURABILITY);
	}

	/**
	 * Decreases the durability of a repair item.
	 *
	 * @param item The item to modify
	 * @param amount The amount to decrease
	 *
	 * @return The modified item, or null if broken
	 */
	@Nullable
	public static ItemStack decreaseDurability(@NotNull ItemStack item, int amount) {
		ItemBuilder builder = new ItemBuilder(item);

		if (!builder.hasNBTTag(RepairKeys.REPAIR_ITEM_DURABILITY)) {
			return null;
		}

		int currentDurability = builder.getIntegerTagData(RepairKeys.REPAIR_ITEM_DURABILITY);
		int newDurability     = currentDurability - amount;

		// Item is broken
		if (newDurability <= 0) {
			return null;
		}

		// Update durability
		builder.modifyTag(RepairKeys.REPAIR_ITEM_DURABILITY, newDurability);

		// Rebuild display name for multi-use items
		String id            = builder.getStringTagData(RepairKeys.REPAIR_ITEM_ID);
		int    maxDurability = builder.getIntegerTagData(RepairKeys.REPAIR_ITEM_MAX_DURABILITY);

		if (maxDurability > 1) {
			String currentDisplayName = builder.getDisplayName();
			// Extract base name (before the «)
			String baseName       = currentDisplayName.split("&8«")[0].trim();
			String newDisplayName = String.format("%s &8«&6%d&7/&6%d&8»", baseName, newDurability, maxDurability);
			builder.setDisplayName(newDisplayName);

			// Update lore
			List<String> currentLore = builder.getLore();
			if (currentLore != null && !currentLore.isEmpty()) {
				for (int i = 0; i < currentLore.size(); i++) {
					if (currentLore.get(i).contains("Uses:")) {
						currentLore.set(i, "&7Uses: &e" + newDurability + "&7/&e" + maxDurability);
						break;
					}
				}
				builder.setLore(currentLore);
			}
		}

		return builder.build();
	}

	/**
	 * Checks if an ItemStack is a repair item.
	 *
	 * @param item The item to check
	 *
	 * @return true if it's a repair item
	 */
	public static boolean isRepairItem(@Nullable ItemStack item) {
		if (item == null || item.getType() == Material.AIR) {
			return false;
		}
		ItemBuilder builder = new ItemBuilder(item);
		return builder.hasNBTTag(RepairKeys.REPAIR_ITEM_ID);
	}

	/**
	 * Gets the repair item ID from an ItemStack.
	 *
	 * @param item The item
	 *
	 * @return The repair item ID, or null if not a repair item
	 */
	@Nullable
	public static String getRepairItemId(@Nullable ItemStack item) {
		if (!isRepairItem(item)) {
			return null;
		}
		ItemBuilder builder = new ItemBuilder(item);
		return builder.getStringTagData(RepairKeys.REPAIR_ITEM_ID);
	}

	/**
	 * Builds a new ItemStack for this repair item.
	 *
	 * @return The built ItemStack
	 */
	@NotNull
	public ItemStack buildItem() {
		return buildItem(maxDurability);
	}

	/**
	 * Builds a new ItemStack with specific durability.
	 *
	 * @param currentDurability The current durability
	 *
	 * @return The built ItemStack
	 */
	@NotNull
	public ItemStack buildItem(int currentDurability) {
		ItemBuilder builder = new ItemBuilder(material);

		// Update display name with durability if multi-use
		String finalDisplayName = displayName;
		if (maxDurability > 1) {
			finalDisplayName = String.format("%s &8«&6%d&7/&6%d&8»", displayName, currentDurability, maxDurability);
		}

		builder.setDisplayName(finalDisplayName);

		// Build lore
		List<String> finalLore = new ArrayList<>(lore);
		finalLore.add("");
		finalLore.add("&7Level: &e" + level);
		if (maxDurability > 1) {
			finalLore.add("&7Uses: &e" + currentDurability + "&7/&e" + maxDurability);
		}

		builder.setLore(finalLore);

		// Apply custom model data if set
		if (customModelData > 0) {
			builder.setCustomModelData(customModelData);
		}

		// Store repair item data in PDC
		builder.addTag(RepairKeys.REPAIR_ITEM_ID, id);
		builder.addTag(RepairKeys.REPAIR_ITEM_DURABILITY, currentDurability);
		builder.addTag(RepairKeys.REPAIR_ITEM_MAX_DURABILITY, maxDurability);

		return builder.build();
	}

	@Override
	public String toString() {
		return String.format("RepairItem{id='%s', displayName='%s', level=%d, maxDurability=%d, effects=%d}",
							 id, displayName, level, maxDurability, effects.size());
	}
}