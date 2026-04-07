package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.inventory.InventoryData;
import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.inventory.handler.SlotContext;
import me.luckyraven.inventory.handler.SlotEventHandler;
import me.luckyraven.inventory.handler.SlotItemFactory;
import me.luckyraven.inventory.part.Slot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private parsing helpers extracted from {@link InventoryAddon} to keep it slim.
 */
final class InventoryParser {

	private InventoryParser() { }

	static void configureSlots(Gangland gangland, int realSize, String slotsStr, FileConfiguration config,
							   List<Slot> slots) {
		if (!slotsStr.endsWith(".")) slotsStr += ".";

		for (int i = 0; i < realSize; ++i) {
			String path    = slotsStr + i;
			var    section = config.getConfigurationSection(path);
			if (section == null) continue;

			Map<String, Object> data = new HashMap<>();
			String              item = getItemInfo(section, data);
			if (item == null) continue;

			String itemName = section.getString("Name");
			if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');
			List<String> lore            = section.getStringList("Lore");
			boolean      enchanted       = section.getBoolean("Enchanted");
			boolean      draggable       = section.getBoolean("Draggable");
			int          customModelData = section.getInt("Custom_Model_Data", 0);
			if (customModelData > 0) data.put("customModelData", customModelData);

			var  conditionSection = section.getConfigurationSection("Condition");
			Slot slot;
			if (conditionSection != null) {
				var conditionalData = ConditionalSlotParser.parse(conditionSection, item, itemName, data, lore,
																  enchanted, draggable);
				slot = new Slot(i, true, draggable, null);
				slot.setConditionalData(conditionalData);
			} else {
				slot = processEventItems(gangland, "Slots", config, i, item, itemName, data, lore, enchanted,
										 draggable);
			}

			slots.add(slot);
		}
	}

	static void configureMultiInventory(Gangland gangland, FileConfiguration config, ConfigurationSection information,
										InventoryData inventoryData) {
		String itemSource = information.getString("Multi.Item_Source");
		int    perPage    = information.getInt("Multi.Per_Page", 28);

		inventoryData.setMultiInventory(true);
		inventoryData.setItemSource(itemSource);
		inventoryData.setPerPage(perPage);

		Map<Integer, Slot> staticItems   = new HashMap<>();
		var                staticSection = config.getConfigurationSection("Static_Items");

		if (staticSection != null) {
			for (String key : staticSection.getKeys(false)) {
				int slotIndex = Integer.parseInt(key);
				var section   = staticSection.getConfigurationSection(key);
				if (section == null) continue;

				Map<String, Object> data = new HashMap<>();
				String              item = getItemInfo(section, data);
				if (item == null) continue;

				String itemName = section.getString("Name");
				if (itemName == null) itemName = item.toLowerCase().replace('_', ' ');
				List<String> lore            = section.getStringList("Lore");
				boolean      enchanted       = section.getBoolean("Enchanted");
				boolean      draggable       = section.getBoolean("Draggable");
				int          customModelData = section.getInt("Custom_Model_Data", 0);
				if (customModelData > 0) data.put("customModelData", customModelData);

				staticItems.put(slotIndex,
								processEventItems(gangland, "Static_Items", config, slotIndex, item, itemName, data,
												  lore, enchanted, draggable));
			}
		}

		inventoryData.setStaticItems(staticItems);
	}

	static List<Action> parseActions(ConfigurationSection eventSection) {
		List<Action> actions     = new ArrayList<>();
		Object       actionValue = eventSection.get("Action");

		if (actionValue instanceof String actionStr) {
			parseAction(actionStr, actions);
		} else if (actionValue instanceof List<?> actionList) {
			for (Object action : actionList) {
				if (action instanceof String actionStr) parseAction(actionStr, actions);
			}
		}

		if (actions.isEmpty()) {
			actions.add(Action.RIGHT_CLICK_AIR);
			actions.add(Action.RIGHT_CLICK_BLOCK);
		}

		return actions;
	}

	@Nullable
	static String getItemInfo(ConfigurationSection section, Map<String, Object> data) {
		if (section.isConfigurationSection("Item")) {
			var itemConfig = section.getConfigurationSection("Item");
			if (itemConfig == null) return null;
			data.put("color", itemConfig.getString("Color"));
			data.put("data", itemConfig.getString("Data"));
			return itemConfig.getString("Type");
		}
		if (section.isString("Item")) return section.getString("Item");
		return null;
	}

	private static Slot processEventItems(Gangland gangland, String basePath, FileConfiguration config, int slotLoc,
										  String item, String itemName, Map<String, Object> data, List<String> lore,
										  boolean enchanted, boolean draggable) {
		String slotsBase = basePath + "." + slotLoc + ".";

		var rightClickSection = config.getConfigurationSection(slotsBase + "OnRightClick");

		InventoryOpener opener = (p, invName) -> InventoryAddon.openInventoryForPlayer(gangland, p, invName);

		for (Map.Entry<String, Class<? extends InventoryEvent>> entry : InventoryAddon.inventoryEvents.entrySet()) {
			var eventSection = config.getConfigurationSection(slotsBase + entry.getKey());
			if (eventSection == null) continue;

			SlotEventHandler handler = InventoryAddon.slotHandlers.get(entry.getValue());
			if (handler == null) continue;

			return handler.handle(
					new SlotContext(eventSection, rightClickSection, slotLoc, item, itemName, data, lore, enchanted,
									draggable), opener);
		}

		for (Map.Entry<String, Class<? extends PlayerEvent>> entry : InventoryAddon.playerEvents.entrySet()) {
			var eventSection = config.getConfigurationSection(slotsBase + entry.getKey());
			if (eventSection == null) continue;

			SlotEventHandler handler = InventoryAddon.slotHandlers.get(entry.getValue());
			if (handler == null) continue;

			return handler.handle(
					new SlotContext(eventSection, rightClickSection, slotLoc, item, itemName, data, lore, enchanted,
									draggable), opener);
		}

		if (rightClickSection != null) {
			return InventoryAddon.slotHandlers.get(InventoryClickEvent.class)
											  .handle(new SlotContext(null, rightClickSection, slotLoc, item, itemName,
																	  data, lore, enchanted, draggable), opener);
		}

		return new Slot(slotLoc, false, draggable, SlotItemFactory.create(item, itemName, data, lore, enchanted));
	}

	private static void parseAction(String value, List<Action> into) {
		try {
			into.add(Action.valueOf(value.toUpperCase()));
		} catch (IllegalArgumentException ignored) { }
	}
}
