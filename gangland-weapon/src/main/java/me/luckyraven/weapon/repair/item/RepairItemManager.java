package me.luckyraven.weapon.repair.item;

import me.luckyraven.weapon.repair.config.RepairItemConfig;
import me.luckyraven.weapon.repair.config.RepairItemData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all repair items in the system.
 */
public class RepairItemManager {

	private final JavaPlugin              plugin;
	private final RepairItemConfig        config;
	private final Map<String, RepairItem> repairItems;

	public RepairItemManager(@NotNull JavaPlugin plugin) {
		this.plugin      = plugin;
		this.config      = new RepairItemConfig(plugin);
		this.repairItems = new HashMap<>();
	}

	/**
	 * Initializes the repair item system.
	 */
	public void initialize() {
		config.load();
		loadAllRepairItems();
	}

	/**
	 * Reloads all repair items from configuration.
	 */
	public void reload() {
		config.reload();
		repairItems.clear();
		loadAllRepairItems();
	}

	/**
	 * Gets a repair item by ID.
	 *
	 * @param id The repair item ID
	 *
	 * @return The repair item, or null if not found
	 */
	@Nullable
	public RepairItem getRepairItem(@NotNull String id) {
		return repairItems.get(id);
	}

	/**
	 * Gets a repair item from an ItemStack.
	 *
	 * @param item The ItemStack
	 *
	 * @return The repair item, or null if not a repair item
	 */
	@Nullable
	public RepairItem getRepairItem(@Nullable ItemStack item) {
		String id = RepairItem.getRepairItemId(item);
		if (id == null) {
			return null;
		}
		return getRepairItem(id);
	}

	/**
	 * Registers a new repair item.
	 *
	 * @param id The repair item ID
	 * @param item The repair item
	 */
	public void registerRepairItem(@NotNull String id, @NotNull RepairItem item) {
		repairItems.put(id, item);
		plugin.getLogger().info("Registered repair item: " + id);
	}

	/**
	 * Unregisters a repair item.
	 *
	 * @param id The repair item ID
	 */
	public void unregisterRepairItem(@NotNull String id) {
		repairItems.remove(id);
		plugin.getLogger().info("Unregistered repair item: " + id);
	}

	/**
	 * Gets all registered repair items.
	 *
	 * @return A map of repair item ID to RepairItem
	 */
	@NotNull
	public Map<String, RepairItem> getAllRepairItems() {
		return new HashMap<>(repairItems);
	}

	/**
	 * Checks if a repair item exists.
	 *
	 * @param id The repair item ID
	 *
	 * @return true if the item exists
	 */
	public boolean hasRepairItem(@NotNull String id) {
		return repairItems.containsKey(id);
	}

	/**
	 * Loads all repair items from configuration.
	 */
	private void loadAllRepairItems() {
		Map<String, RepairItemData> dataMap = config.getAllRepairItems();

		for (Map.Entry<String, RepairItemData> entry : dataMap.entrySet()) {
			try {
				RepairItemData data = entry.getValue();
				RepairItem item = new RepairItem(
						data.getId(),
						data.getDisplayName(),
						data.getMaterial(),
						data.getLevel(),
						data.getDurability(),
						data.getLore(),
						data.getEffects(),
						data.getMetadata(),
						data.getCustomModelData()
				);
				repairItems.put(entry.getKey(), item);
			} catch (Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Failed to create repair item: " + entry.getKey(), e);
			}
		}

		plugin.getLogger().info("Loaded " + repairItems.size() + " repair items");
	}
}