package me.luckyraven.file.configuration.inventory;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.itemsource.GangItemSourceProvider;
import me.luckyraven.inventory.*;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryAddon {

	private static final Map<String, InventoryBuilder>                inventories     = new HashMap<>();
	private static final Map<String, Class<? extends PlayerEvent>>    playerEvents    = new HashMap<>();
	private static final Map<String, Class<? extends InventoryEvent>> inventoryEvents = new HashMap<>();

	private static ItemSourceProvider itemSourceProvider;

	static {
		// initialize player events
		playerEvents.put("OnItemClick", PlayerInteractEvent.class);

		// initialize inventory events
		inventoryEvents.put("OnClick", InventoryClickEvent.class);
		inventoryEvents.put("OnInteract", InventoryInteractEvent.class);
		inventoryEvents.put("OnClose", InventoryCloseEvent.class);
		inventoryEvents.put("OnInventory", InventoryEvent.class);
	}

	public static void setItemSourceProvider(Gangland gangland) {
		itemSourceProvider = new GangItemSourceProvider(gangland);
	}

	@Nullable
	public static InventoryBuilder getInventory(String key) {
		return inventories.get(key);
	}

	public static Set<String> getInventoryKeys() {
		return inventories.keySet();
	}

	public static int size() {
		return inventories.size();
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
		configureSlots(gangland, realSize, slotsStr, config, slots);

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

		// Handle multi-inventory configuration
		if (type.equalsIgnoreCase("multi-inventory")) {
			configureMultiInventory(gangland, config, information, inventoryData);
		}

		if (state != null) inventoryData.setOpenInventory(new OpenInventory(state, value, openPermission));

		inventories.put(name, new InventoryBuilder(inventoryData, permission));
	}

	private static void configureMultiInventory(Gangland gangland, FileConfiguration config, String information,
												InventoryData inventoryData) {
		String itemSource = config.getString(information + "Multi.Item_Source");
		int    perPage    = config.getInt(information + "Multi.Per_Page", 28);

		inventoryData.setMultiInventory(true);
		inventoryData.setItemSource(itemSource);
		inventoryData.setPerPage(perPage);

		// Process static items for multi-inventory
		Map<Integer, Slot>   staticItems   = new HashMap<>();
		ConfigurationSection staticSection = config.getConfigurationSection("Static_Items");

		if (staticSection != null) {
			for (String key : staticSection.getKeys(false)) {
				int slotIndex = Integer.parseInt(key);


				ConfigurationSection section = staticSection.getConfigurationSection(key);
				if (section == null) continue;

				Map<String, Object> data = new HashMap<>();
				String              item = getItemInfo(section, data);

				if (item == null) continue;

				String itemName = section.getString("Name");
				if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');

				List<String> lore      = section.getStringList("Lore");
				boolean      enchanted = section.getBoolean("Enchanted");
				boolean      draggable = section.getBoolean("Draggable");

				Slot slot = processEventItems(gangland, "Static_Items", config, slotIndex, item, itemName, data, lore,
											  enchanted, draggable);
				staticItems.put(slotIndex, slot);
			}
		}

		inventoryData.setStaticItems(staticItems);
	}

	private static @Nullable String getItemInfo(ConfigurationSection section, Map<String, Object> data) {
		String item;

		if (section.isConfigurationSection("Item")) {
			ConfigurationSection itemConfig = section.getConfigurationSection("Item");

			if (itemConfig != null) {
				item = itemConfig.getString("Type");

				data.put("color", itemConfig.getString("Color"));
				data.put("data", itemConfig.getString("Data"));
			} else item = null;
		} else if (section.isString("Item")) item = section.getString("Item");
		else item = null;

		return item;
	}

	private static void configureSlots(Gangland gangland, int realSize, String slotsStr, FileConfiguration config,
									   List<Slot> slots) {
		for (int slot = 0; slot < realSize; ++slot) {
			String sectionStr = slotsStr + slot + ".";
			String substring  = sectionStr.substring(0, sectionStr.length() - 1);

			ConfigurationSection section = config.getConfigurationSection(substring);
			if (section == null) continue;

			Map<String, Object> data = new HashMap<>();
			String              item = getItemInfo(section, data);

			if (item == null) continue;

			String itemName = section.getString("Name");
			if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');
			List<String> lore      = section.getStringList("Lore");
			boolean      enchanted = section.getBoolean("Enchanted");
			boolean      draggable = section.getBoolean("Draggable");

			slots.add(processEventItems(gangland, "Slots", config, slot, item, itemName, data, lore, enchanted,
										draggable));
		}
	}

	private static Slot processEventItems(Gangland gangland, String basePath, FileConfiguration config, int slotLoc,
										  String item, String itemName, Map<String, Object> data, List<String> lore,
										  boolean enchanted, boolean draggable) {
		String slotsBase = basePath + "." + slotLoc + ".";

		for (String event : inventoryEvents.keySet()) {
			String eventSectionString = slotsBase + event + ".";
			String substring          = eventSectionString.substring(0, eventSectionString.length() - 1);
			var    eventSection       = config.getConfigurationSection(substring);
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
				User<Player> user = gangland.getInitializer().getUserManager().getUser(player);

				InventoryHandler existingInventory = user.getInventory(slotInventory);

				if (existingInventory != null) {
					existingInventory.open(player);
					return;
				}

				InventoryBuilder invBuilder = inventories.get(slotInventory);

				if (invBuilder == null) return;

				Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());
				Fill line = new Fill(SettingAddon.getInventoryLineName(), SettingAddon.getInventoryLineItem());

				// Check if it's a multi-inventory
				if (invBuilder.inventoryData().isMultiInventory()) {
					String          itemSource = invBuilder.inventoryData().getItemSource();
					List<ItemStack> items      = itemSourceProvider.getItems(player, itemSource);

					ButtonTags buttonTags = new ButtonTags(SettingAddon.getPreviousPage(), SettingAddon.getHomePage(),
														   SettingAddon.getNextPage());

					MultiInventory multiInventory = invBuilder.createMultiInventory(gangland, gangland, player, items,
																					buttonTags, fill);

					if (multiInventory != null) {
						multiInventory.open(player);
						user.addInventory(multiInventory);
					}
				} else {
					InventoryHandler handler = invBuilder.createInventory(gangland, gangland, user.getUser(), fill,
																		  line);
					handler.open(player);
					user.addInventory(handler);
				}
			}
		});

		return slot;
	}

	private static ItemBuilder createItem(String item, String itemName, Map<String, Object> data, List<String> lore,
										  boolean enchanted) {
		ItemBuilder itemBuilder = new ItemBuilder(validateItem(item).get());

		String color    = (String) data.get("color");
		String dataInfo = (String) data.get("data");

		if (color != null) itemBuilder.addTag("color", color);
		if (dataInfo != null) itemBuilder.addTag("data", dataInfo);

		itemBuilder.setDisplayName(itemName);
		itemBuilder.setLore(lore);
		if (enchanted) itemBuilder.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
								  .addItemFlags(ItemFlag.HIDE_ENCHANTS);

		return itemBuilder;
	}

	private static XMaterial validateItem(String value) {
		XMaterial      xMaterial;
		MaterialType[] materialTypes = MaterialType.values();
		if (Arrays.stream(materialTypes).anyMatch(materialType -> materialType.name().contains(value))) xMaterial
				= XMaterial.matchXMaterial("BLACK_" + value).orElse(XMaterial.BLACK_WOOL);
		else xMaterial = XMaterial.valueOf(value);

		return xMaterial;
	}

}
