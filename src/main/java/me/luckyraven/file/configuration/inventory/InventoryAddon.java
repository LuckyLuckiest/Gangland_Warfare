package me.luckyraven.file.configuration.inventory;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.InventoryBuilder;
import me.luckyraven.data.inventory.InventoryData;
import me.luckyraven.data.inventory.OpenInventory;
import me.luckyraven.data.inventory.State;
import me.luckyraven.data.inventory.part.Slot;
import me.luckyraven.data.user.User;
import me.luckyraven.file.FileHandler;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryAddon {

	private static final Map<String, InventoryBuilder>                inventories     = new HashMap<>();
	private static final Map<String, Class<? extends PlayerEvent>>    playerEvents    = new HashMap<>();
	private static final Map<String, Class<? extends InventoryEvent>> inventoryEvents = new HashMap<>();

	static {
		// initialize player events
		playerEvents.put("OnItemClick", PlayerInteractEvent.class);

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

	public static Set<String> getInventoryKeys() {
		return inventories.keySet();
	}

	public static void registerInventory(Gangland gangland, FileHandler fileHandler) {
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            fileName = fileHandler.getName().toLowerCase();

		String configVersion = config.getString("Config_Version");
		if (configVersion != null) {
			// recreates the file if needed
			return;
		}

		String information = "Information.";
		String slotsStr    = "Slots.";

		// information section
		String name = config.getString(information + "Name");
		if (name == null || name.isEmpty()) name = fileName;
		String displayName = Objects.requireNonNull(config.getString(information + "Display_Name"));
		int    size        = config.getInt(information + "Size");
		String permission  = config.getString(information + "Permission");

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

		if (permission != null) gangland.getInitializer().getPermissionManager().addPermission(permission);
		int realSize = InventoryHandler.factorOfNine(size);

		List<Slot> slots = new ArrayList<>();

		// slots section
		for (int slot = 0; slot < realSize; ++slot) {
			String sectionStr = slotsStr + slot + ".";
			ConfigurationSection section = config.getConfigurationSection(
					sectionStr.substring(0, sectionStr.length() - 1));
			if (section == null) continue;

			String              item;
			Map<String, Object> data = new HashMap<>();
			if (section.isConfigurationSection("Item")) {
				ConfigurationSection itemConfig = section.getConfigurationSection("Item");

				if (itemConfig != null) {
					item = itemConfig.getString("Type");

					data.put("color", itemConfig.getString("Color"));
					data.put("data", itemConfig.getString("Data"));
				} else item = null;
			} else if (section.isString("Item")) item = section.getString("Item");
			else item = null;

			if (item == null) continue;

			String itemName = section.getString("Name");
			if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');
			List<String> lore      = section.getStringList("Lore");
			boolean      enchanted = section.getBoolean("Enchanted");
			boolean      draggable = section.getBoolean("Draggable");

			slots.add(processEventItems(gangland, config, slot, item, itemName, data, lore, enchanted, draggable));
		}

		boolean       configFill       = config.getBoolean(information + "Configuration.Fill");
		boolean       configBorder     = config.getBoolean(information + "Configuration.Border");
		List<Integer> configVertical   = config.getIntegerList(information + "Configuration.Line.Vertical");
		List<Integer> configHorizontal = config.getIntegerList(information + "Configuration.Line.Horizontal");

		// open the inventory according to these states
		State  state = null;
		String value = null;
		if (openCommand != null) {
			state = State.COMMAND;
			value = openCommand;
		} else if (openEvent != null) {
			state = State.EVENT;
			value = openEvent;
		}

		InventoryData inventoryData = new InventoryData(name, displayName, type, size);
		inventoryData.addAllSlots(slots);
		inventoryData.setPermission(permission);
		inventoryData.setVerticalLine(configVertical);
		inventoryData.setHorizontalLine(configHorizontal);
		inventoryData.setFill(configFill);
		inventoryData.setBorder(configBorder);

		if (state != null) inventoryData.setOpenInventory(new OpenInventory(state, value, openPermission));

		inventories.put(name, new InventoryBuilder(inventoryData, permission));
	}

	private static Slot processEventItems(Gangland gangland, FileConfiguration config, int slotLoc, String item,
	                                      String itemName, Map<String, Object> data, List<String> lore,
	                                      boolean enchanted, boolean draggable) {
		for (String event : inventoryEvents.keySet()) {
			String eventSectionStr = "Slots." + slotLoc + "." + event + ".";
			ConfigurationSection eventSection = config.getConfigurationSection(
					eventSectionStr.substring(0, eventSectionStr.length() - 1));
			if (eventSection == null) continue;

			// so far, there is support for clickable events
			if (inventoryEvents.get(event).equals(InventoryClickEvent.class)) {
				return inventoryClickEvent(gangland, eventSection, slotLoc, item, itemName, data, lore, enchanted,
				                           draggable);
			} else if (inventoryEvents.get(event).equals(InventoryInteractEvent.class)) {

			} else if (inventoryEvents.get(event).equals(InventoryCloseEvent.class)) {

			} else if (inventoryEvents.get(event).equals(InventoryEvent.class)) {

			}
		}

		// add the slot if it doesn't contain an event
		ItemBuilder itemBuilder = createItem(item, itemName, data, lore, enchanted);

		return new Slot(slotLoc, false, draggable, itemBuilder);
	}

	private static Slot inventoryClickEvent(Gangland gangland, ConfigurationSection eventSection, int slotLoc,
	                                        String item, String itemName, Map<String, Object> data, List<String> lore,
	                                        boolean enchanted, boolean draggable) {
		String slotCommand    = eventSection.getString("Command");
		String slotInventory  = eventSection.getString("Inventory");
		String slotPermission = eventSection.getString("Permission");

		ItemBuilder itemBuilder = createItem(item, itemName, data, lore, enchanted);

		Slot slot = new Slot(slotLoc, true, draggable, itemBuilder);

		slot.setClickable((player, inventoryHandler, builder) -> {
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

				InventoryHandler handler = invBuilder.createInventory(gangland, user, slotInventory);
				handler.open(player);
			}
		});

		return slot;
	}

	private static ItemBuilder createItem(String item, String itemName, Map<String, Object> data, List<String> lore,
	                                      boolean enchanted) {
		ItemBuilder itemBuilder = new ItemBuilder(validateItem(item).parseMaterial());

		String color    = (String) data.get("color");
		String dataInfo = (String) data.get("data");

		if (color != null) itemBuilder.addTag("color", color);
		if (dataInfo != null) itemBuilder.addTag("data", dataInfo);

		itemBuilder.setDisplayName(itemName);
		itemBuilder.setLore(lore);
		if (enchanted) itemBuilder.addEnchantment(Enchantment.DURABILITY, 1).addItemFlags(ItemFlag.HIDE_ENCHANTS);

		return itemBuilder;
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
