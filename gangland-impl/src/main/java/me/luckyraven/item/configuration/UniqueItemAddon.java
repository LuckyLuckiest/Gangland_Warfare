package me.luckyraven.item.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.Setter;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.exception.PluginException;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.item.contract.UniqueItemRegistry;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

@CustomLog
public class UniqueItemAddon implements Comparator<UniqueItem>, UniqueItemRegistry, FileInitializer {

	private final Map<String, UniqueItem> uniqueItems;
	private final PermissionManager       permissionManager;
	private final FileHandler             fileHandler;
	private final FuelService             fuelService;

	/**
	 * Placeholder resolver injected by the impl side so each loaded {@link UniqueItem} can resolve {@code %gangland_*%}
	 * tokens in its display name and lore at item-build time.
	 */
	@Setter
	@Nullable
	private Placeholder placeholder;

	public UniqueItemAddon(PermissionManager permissionManager, FileManager fileManager, FuelService fuelService) {
		this.uniqueItems       = new HashMap<>();
		this.permissionManager = permissionManager;
		this.fuelService       = fuelService;

		try {
			String fileName = "unique_items";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		registerUniqueItem(permissionManager, fileHandler.getFileConfiguration());
	}

	public UniqueItem getUniqueItem(String key) {
		return uniqueItems.get(key);
	}

	public Map<String, UniqueItem> getUniqueItems() {
		return Collections.unmodifiableMap(uniqueItems);
	}

	public void clear() {
		uniqueItems.clear();
	}

	@Override
	public int compare(UniqueItem item1, UniqueItem item2) {
		return item1.compareTo(item2.buildItem());
	}

	private void registerUniqueItem(PermissionManager permissionManager, FileConfiguration uniqueItems) {
		List<String> temp = new ArrayList<>();

		// initialize the data
		for (String key : uniqueItems.getKeys(false)) {
			var section = uniqueItems.getConfigurationSection(key);

			if (section == null) continue;

			String permission      = section.getString("Permission");
			String materialString  = section.getString("Material");
			int    customModelData = section.getInt("Custom_Model_Data", 0);
			String name            = section.getString("Name");

			// configuration items that can be not available
			List<String> lore = section.getStringList("Lore");

			// read the loot key for lockpick/key functionality
			String lootKey = section.getString("Loot_Key");

			var inventorySection = section.getConfigurationSection("Inventory");

			if (inventorySection == null) continue;

			boolean addOnJoin       = inventorySection.getBoolean("Add_On_Join");
			boolean addOnRespawn    = inventorySection.getBoolean("Add_On_Respawn");
			boolean dropOnDeath     = inventorySection.getBoolean("Drop_On_Death");
			boolean allowDuplicates = inventorySection.getBoolean("Allow_Duplicates");

			int slot = inventorySection.getInt("Slot");

			boolean overrides = inventorySection.getBoolean("Overrides");
			boolean movable   = inventorySection.getBoolean("Movable");
			boolean droppable = inventorySection.getBoolean("Droppable");

			if (materialString == null || name == null) continue;

			var xMaterialOptional = XMaterial.matchXMaterial(materialString);
			var material          = xMaterialOptional.orElse(XMaterial.BARRIER);

			boolean addToInventory = slot > -1;

			// Fuel section (optional)
			Fuel                 fuel        = null;
			ConfigurationSection fuelSection = section.getConfigurationSection("Fuel");
			if (fuelSection != null) {
				String    fuelKey            = fuelSection.getString("Fuel_Key", "");
				int       maxFuel            = fuelSection.getInt("Max_Fuel", 6000);
				int       fuelPerItem        = fuelSection.getInt("Fuel_Per_Item", 1200);
				String    fuelMaterialString = fuelSection.getString("Fuel_Material", "COAL");
				XMaterial fuelMaterial       = XMaterial.matchXMaterial(fuelMaterialString).orElse(XMaterial.COAL);

				fuel = Fuel.builder()
				           .fuelKey(fuelKey)
				           .maxFuel(maxFuel)
				           .displayName(name)
				           .fuelMaterial(fuelMaterial)
				           .fuelPerItem(fuelPerItem)
				           .build();

				fuelService.registerFuel(fuel);
			}

			var uniqueItem = UniqueItem.builder()
			                           .permission(permission)
			                           .uniqueItem(key)
			                           .material(material.get())
			                           .customModelData(customModelData)
			                           .name(name)
			                           .addOnJoin(addOnJoin)
			                           .addOnRespawn(addOnRespawn)
			                           .dropOnDeath(dropOnDeath)
			                           .allowDuplicates(allowDuplicates)
			                           .addToInventory(addToInventory)
			                           .lore(lore)
			                           .inventorySlot(slot)
			                           .overridesSlot(overrides)
			                           .movable(movable)
			                           .droppable(droppable)
			                           .lootKey(lootKey)
			                           .fuel(fuel)
			                           .placeholder(placeholder)
			                           .build();

			this.uniqueItems.put(key, uniqueItem);
			temp.add(key);

			// add the permission
			permissionManager.addPermission(permission);
		}

		log.info("Loaded the following unique items:");
		log.info(temp);
	}

}
