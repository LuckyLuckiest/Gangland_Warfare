package me.luckyraven.file.configuration.inventory;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.InventoryBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.file.FileHandler;
import me.luckyraven.util.InventoryUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryAddon {

	private static final Map<String, InventoryBuilder>                inventories     = new HashMap<>();
	private static final Map<String, Class<? extends PlayerEvent>>    playerEvents    = new HashMap<>();
	private static final Map<String, Class<? extends InventoryEvent>> inventoryEvents = new HashMap<>();

	static {
		// initialize player events
//		loadEventSubclasses(playerEvents, PlayerEvent.class);

		// initialize inventory events
		inventoryEvents.put("OnClick", InventoryClickEvent.class);
		inventoryEvents.put("OnInteract", InventoryInteractEvent.class);
		inventoryEvents.put("OnClose", InventoryCloseEvent.class);
		inventoryEvents.put("OnInventory", InventoryEvent.class);
	}

	@Nullable
	public static InventoryBuilder getInventory(String key) {
		return inventories.get(key);
	}

	public static Map<String, InventoryBuilder> getInventories() {
		return new HashMap<>(inventories);
	}

	public static void registerInventory(Gangland gangland, FileHandler fileHandler) {
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            tempName = fileHandler.getName().toLowerCase();

		String information = "Information.";
		String slots       = "Slots.";

		String configVersion = config.getString("Config_Version");
		if (configVersion != null) {
			// recreates the file if needed
			return;
		}

		// information section
		String name = config.getString(information + "Name");
		if (name == null || name.isEmpty()) name = tempName.substring(0, tempName.lastIndexOf('.'));
		String displayName = Objects.requireNonNull(config.getString(information + "Display_Name"));
		int    size        = config.getInt(information + "Size");

		// the type would determine if it was an inventory handler or multi inventory
		// nothing more since it is exhausting
		String type = Objects.requireNonNull(config.getString(information + "Type"));

		String tempOpenCommand = config.getString(information + "Open.Command");
		String openCommand     = null;
		if (tempOpenCommand != null) {
			openCommand = tempOpenCommand.startsWith("/") ? tempOpenCommand : "/" + tempOpenCommand;
			openCommand = openCommand.strip();
		}
		String openEvent      = config.getString(information + "Open.Event");
		String openPermission = config.getString(information + "Open.Permission");

		boolean       configFill       = config.getBoolean(information + "Configuration.Fill");
		boolean       configBorder     = config.getBoolean(information + "Configuration.Border");
		List<Integer> configVertical   = config.getIntegerList(information + "Configuration.Line.Vertical");
		List<Integer> configHorizontal = config.getIntegerList(information + "Configuration.Line.Horizontal");

		InventoryHandler inventoryHandler = new InventoryHandler(gangland, displayName, size, name, true);

		// slots section
		for (int slot = 0; slot < inventoryHandler.getSize(); ++slot) {
			String sectionStr = slots + slot + ".";
			ConfigurationSection section = config.getConfigurationSection(
					sectionStr.substring(0, sectionStr.length() - 1));
			if (section == null) continue;

			String item;
			String color = null;
			if (section.isConfigurationSection("Item")) {
				ConfigurationSection itemConfig = section.getConfigurationSection("Item");

				if (itemConfig != null) {
					item = itemConfig.getString("Type");
					color = itemConfig.getString("Color");
				} else item = null;
			} else if (section.isString("Item")) item = section.getString("Item");
			else item = null;

			if (item == null) continue;

			String itemName = section.getString("Name");
			if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');
			List<String> lore      = section.getStringList("Lore");
			boolean      enchanted = section.getBoolean("Enchanted");
			boolean      draggable = section.getBoolean("Draggable");

			for (String event : inventoryEvents.keySet()) {
				String eventSectionStr = sectionStr + event + ".";
				ConfigurationSection eventSection = config.getConfigurationSection(
						eventSectionStr.substring(0, eventSectionStr.length() - 1));
				if (eventSection == null) continue;

				// so far, there is support for clickable events
				if (!inventoryEvents.get(event).equals(InventoryClickEvent.class)) return;

				String slotCommand    = eventSection.getString("Command");
				String slotInventory  = eventSection.getString("Inventory");
				String slotPermission = eventSection.getString("Permission");

				ItemBuilder itemBuilder = new ItemBuilder(validateItem(item).parseMaterial());

				if (color != null) itemBuilder.addTag("color", color);
				itemBuilder.setDisplayName(itemName);
				itemBuilder.setLore(lore);
				if (enchanted) {
					itemBuilder.addEnchantment(Enchantment.DURABILITY, 1).addItemFlags(ItemFlag.HIDE_ENCHANTS);
				}

				inventoryHandler.setItem(slot, itemBuilder, draggable, (player, inventory, builder) -> {
					if (slotPermission != null) {
						if (!player.hasPermission(slotPermission)) return;
					}

					if (slotCommand != null) {
						String command = slotCommand.startsWith("/") ? slotCommand.substring(1) : slotCommand;
						player.performCommand(command);
					}
					if (slotInventory != null) {
						User<Player>     user       = gangland.getInitializer().getUserManager().getUser(player);
						InventoryBuilder invBuilder = inventories.get(slotInventory);

						if (invBuilder == null) return;

						InventoryHandler handler = InventoryBuilder.initInventory(gangland, user, slotInventory,
						                                                          invBuilder);
						handler.open(player);
					}
				});

				break;
			}

			if (!inventoryHandler.itemOccupied(slot)) {
				inventoryHandler.setItem(slot, validateItem(item).parseMaterial(), itemName, lore, enchanted,
				                         draggable);
			}
		}

		if (configFill) InventoryUtil.fillInventory(inventoryHandler);
		else if (configBorder) InventoryUtil.createBoarder(inventoryHandler);

		if (!configVertical.isEmpty()) {
			for (int column : configVertical)
				InventoryUtil.verticalLine(inventoryHandler, column);
		}

		if (!configHorizontal.isEmpty()) {
			for (int row : configHorizontal)
				InventoryUtil.horizontalLine(inventoryHandler, row);
		}

		InventoryBuilder builder = new InventoryBuilder(inventoryHandler);

		// open the inventory according to these states
		InventoryBuilder.State state = null;
		String                 value = null;
		if (openCommand != null) {
			state = InventoryBuilder.State.COMMAND;
			value = openCommand;
		} else if (openEvent != null) {
			state = InventoryBuilder.State.EVENT;
			value = openEvent;
		}

		if (state != null) builder.addOpen(state, value, openPermission);

		inventories.put(name, builder);
	}

	private static XMaterial validateItem(String value) {
		XMaterial      xMaterial;
		MaterialType[] materialTypes = MaterialType.values();
		if (Arrays.stream(materialTypes).anyMatch(materialType -> materialType.name().contains(value)))
			xMaterial = XMaterial.matchXMaterial("BLACK_" + value).orElse(XMaterial.BLACK_WOOL);
		else xMaterial = XMaterial.valueOf(value);

		return xMaterial;
	}

}
