package me.luckyraven.item.configuration;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.util.item.unique.UniqueItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.*;

public class UniqueItemAddon implements Comparator<UniqueItem> {

	private static final Logger logger = LogManager.getLogger(UniqueItemAddon.class.getSimpleName());

	private final Map<String, UniqueItem> uniqueItems;
	private final PermissionManager       permissionManager;
	private final FileManager             fileManager;

	public UniqueItemAddon(PermissionManager permissionManager, FileManager fileManager) {
		this.uniqueItems       = new HashMap<>();
		this.fileManager       = fileManager;
		this.permissionManager = permissionManager;
	}

	public void initialize() {
		FileConfiguration fileConfiguration;
		try {
			String fileName = "unique_items";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));
			fileConfiguration = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		registerUniqueItem(permissionManager, fileConfiguration);
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

			String permission     = section.getString("Permission");
			String materialString = section.getString("Material");
			String name           = section.getString("Name");

			// configuration items that can be not available
			List<String> lore = section.getStringList("Lore");

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

			var uniqueItem = UniqueItem.builder()
									   .permission(permission)
									   .uniqueItem(key)
									   .material(material.get())
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
									   .build();

			this.uniqueItems.put(key, uniqueItem);
			temp.add(key);

			// add the permission
			permissionManager.addPermission(permission);
		}

		logger.info("Loaded the following unique items:");
		logger.info(temp);
	}

}
