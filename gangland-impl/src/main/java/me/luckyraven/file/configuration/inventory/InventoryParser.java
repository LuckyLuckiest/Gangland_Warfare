package me.luckyraven.file.configuration.inventory;

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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Package-private parsing helpers used by {@link InventoryRuntimeContext} during inventory registration.
 */
final class InventoryParser {

	private InventoryParser() { }

	static void configureSlots(InventoryRuntimeContext runtimeContext, int realSize, String slotsStr,
	                           FileConfiguration config, List<Slot> slots) {
		if (!slotsStr.endsWith(".")) slotsStr += ".";

		for (int i = 0; i < realSize; ++i) {
			String path    = slotsStr + i;
			var    section = config.getConfigurationSection(path);
			if (section == null) continue;

			Map<String, Object> data = new HashMap<>();
			String              item = getItemInfo(section, data);
			if (item == null) continue;

			String itemName = section.getString("Name");
			// Auto-generate a name only for raw materials. For prefixed items (e.g. weapon:awp) leave it null so
			// SlotItemFactory keeps the parser-supplied display name.
			if (itemName == null && !item.contains(":")) itemName = item.toLowerCase().replace('_', ' ');
			List<String> lore            = section.getStringList("Lore");
			boolean      enchanted       = section.getBoolean("Enchanted");
			boolean      draggable       = section.getBoolean("Draggable");
			int          customModelData = section.getInt("Custom_Model_Data", 0);
			if (customModelData > 0) data.put("customModelData", customModelData);

			var  conditionSection = section.getConfigurationSection("Condition");
			Slot slot;
			if (conditionSection != null) {
				var conditionalData = ConditionalSlotParser.parse(runtimeContext.itemResolver(), conditionSection, item,
				                                                  itemName, data, lore, enchanted, draggable);
				slot = new Slot(i, true, draggable, null);
				slot.setConditionalData(conditionalData);
			} else {
				slot = processEventItems(runtimeContext, "Slots", config, i, item, itemName, data, lore, enchanted,
				                         draggable);
			}

			slots.add(slot);
		}
	}

	static void configureMultiInventory(InventoryRuntimeContext runtimeContext, FileConfiguration config,
	                                    ConfigurationSection information, InventoryData inventoryData) {
		String itemSource = information.getString("Multi.Item_Source");
		int    perPage    = information.getInt("Multi.Per_Page", 28);

		inventoryData.setMultiInventory(true);
		inventoryData.setItemSource(itemSource);
		inventoryData.setPerPage(perPage);

		configureItemTemplate(runtimeContext, information, inventoryData);

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
				if (itemName == null && !item.contains(":")) itemName = item.toLowerCase().replace('_', ' ');
				List<String> lore            = section.getStringList("Lore");
				boolean      enchanted       = section.getBoolean("Enchanted");
				boolean      draggable       = section.getBoolean("Draggable");
				int          customModelData = section.getInt("Custom_Model_Data", 0);
				if (customModelData > 0) data.put("customModelData", customModelData);

				Slot staticItem = processEventItems(runtimeContext, "Static_Items", config, slotIndex, item, itemName,
				                                    data, lore, enchanted, draggable);
				staticItems.put(slotIndex, staticItem);
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

	/**
	 * Parses {@code Information.Item_Template} — the per-entry template used to render each row produced by the
	 * {@code ItemSourceProvider}. The template's Item / Name / Lore / Data carry placeholders that the renderer
	 * substitutes per entry at open time. {@code OnClick.Command} is optional; its value is also a per-entry template.
	 */
	private static void configureItemTemplate(InventoryRuntimeContext runtimeContext, ConfigurationSection information,
	                                          InventoryData inventoryData) {
		ConfigurationSection templateSection = information.getConfigurationSection("Item_Template");
		if (templateSection == null) return;

		Map<String, Object> data = new HashMap<>();
		String              item = getItemInfo(templateSection, data);
		if (item == null) return;

		String itemName = templateSection.getString("Name");
		if (itemName == null && !item.contains(":")) itemName = item.toLowerCase().replace('_', ' ');
		List<String> lore            = templateSection.getStringList("Lore");
		boolean      enchanted       = templateSection.getBoolean("Enchanted");
		int          customModelData = templateSection.getInt("Custom_Model_Data", 0);
		if (customModelData > 0) data.put("customModelData", customModelData);

		Function<String, ItemStack> itemResolver = runtimeContext.itemResolver();
		Slot templateSlot = new Slot(0, false, false,
		                             SlotItemFactory.create(itemResolver, item, itemName, data, lore, enchanted));

		inventoryData.setItemTemplate(templateSlot);

		ConfigurationSection onClickSection = templateSection.getConfigurationSection("OnClick");
		if (onClickSection != null) {
			String command = onClickSection.getString("Command");
			if (command != null) inventoryData.setItemTemplateCommand(command);
		}
	}

	private static Slot processEventItems(InventoryRuntimeContext runtimeContext, String basePath,
	                                      FileConfiguration config, int slotLoc, String item, String itemName,
	                                      Map<String, Object> data, List<String> lore, boolean enchanted,
	                                      boolean draggable) {
		String slotsBase = basePath + "." + slotLoc + ".";

		var rightClickSection = config.getConfigurationSection(slotsBase + "OnRightClick");

		InventoryOpener             opener          = runtimeContext::openInventoryForPlayer;
		InventoryDefinitionStore    definitionStore = runtimeContext.definitionStore();
		Function<String, ItemStack> itemResolver    = runtimeContext.itemResolver();

		for (Map.Entry<String, Class<? extends InventoryEvent>> entry : definitionStore.inventoryEvents().entrySet()) {
			var eventSection = config.getConfigurationSection(slotsBase + entry.getKey());
			if (eventSection == null) continue;

			SlotEventHandler handler = definitionStore.slotHandlers().get(entry.getValue());
			if (handler == null) continue;

			return handler.handle(
					new SlotContext(eventSection, rightClickSection, slotLoc, item, itemName, data, lore, enchanted,
					                draggable, itemResolver), opener);
		}

		for (Map.Entry<String, Class<? extends PlayerEvent>> entry : definitionStore.playerEvents().entrySet()) {
			var eventSection = config.getConfigurationSection(slotsBase + entry.getKey());
			if (eventSection == null) continue;

			SlotEventHandler handler = definitionStore.slotHandlers().get(entry.getValue());
			if (handler == null) continue;

			return handler.handle(
					new SlotContext(eventSection, rightClickSection, slotLoc, item, itemName, data, lore, enchanted,
					                draggable, itemResolver), opener);
		}

		if (rightClickSection != null) {
			return definitionStore.slotHandlers()
			                      .get(InventoryClickEvent.class)
			                      .handle(new SlotContext(null, rightClickSection, slotLoc, item, itemName, data, lore,
			                                              enchanted, draggable, itemResolver), opener);
		}

		return new Slot(slotLoc, false, draggable,
		                SlotItemFactory.create(itemResolver, item, itemName, data, lore, enchanted));
	}

	private static void parseAction(String value, List<Action> into) {
		try {
			into.add(Action.valueOf(value.toUpperCase()));
		} catch (IllegalArgumentException ignored) { }
	}
}
