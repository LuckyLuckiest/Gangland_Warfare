package org.luckyraven.gangland.file.configuration.inventory;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.inventory.condition.ConditionalSlotData;
import org.luckyraven.gangland.inventory.condition.SlotCondition;
import org.luckyraven.gangland.inventory.handler.SlotItemFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Parses conditional slot data (True/False branches with nested conditions) from YAML.
 */
final class ConditionalSlotParser {

	private ConditionalSlotParser() { }

	static ConditionalSlotData parse(@Nullable Function<String, ItemStack> itemResolver,
	                                 ConfigurationSection conditionSection, String defaultItem, String defaultName,
	                                 Map<String, Object> defaultData, List<String> defaultLore,
	                                 boolean defaultEnchanted, boolean defaultDraggable) {
		String valueExpression = conditionSection.getString("Value");
		if (valueExpression == null || valueExpression.isEmpty()) {
			throw new IllegalArgumentException("Condition must have a Value");
		}

		SlotCondition condition = new SlotCondition(valueExpression);

		var trueSection = conditionSection.getConfigurationSection("True");
		if (trueSection == null) trueSection = conditionSection.getConfigurationSection("true");

		var falseSection = conditionSection.getConfigurationSection("False");
		if (falseSection == null) falseSection = conditionSection.getConfigurationSection("false");

		var trueData = parseBranchData(itemResolver, trueSection, defaultItem, defaultName, defaultData, defaultLore,
		                               defaultEnchanted, defaultDraggable);
		var falseData = parseBranchData(itemResolver, falseSection, defaultItem, defaultName, defaultData, defaultLore,
		                                defaultEnchanted, defaultDraggable);

		return new ConditionalSlotData(condition, trueData, falseData);
	}

	static ConditionalSlotData.BranchData parseBranchData(@Nullable Function<String, ItemStack> itemResolver,
	                                                      @Nullable ConfigurationSection branchSection,
	                                                      String defaultItem, String defaultName,
	                                                      Map<String, Object> defaultData, List<String> defaultLore,
	                                                      boolean defaultEnchanted, boolean defaultDraggable) {
		if (branchSection == null) {
			ItemBuilder item = SlotItemFactory.create(itemResolver, defaultItem, defaultName, defaultData, defaultLore,
			                                          defaultEnchanted);
			return new ConditionalSlotData.BranchData(item, defaultName, defaultLore, false, defaultDraggable, null,
			                                          null, null);
		}

		Map<String, Object> itemData = new HashMap<>();
		String              item     = InventoryParser.getItemInfo(branchSection, itemData);
		if (item == null) item = defaultItem;

		String name = branchSection.getString("Name", defaultName);
		// Auto-generate a fallback name only for raw materials. Prefixed items keep the parser-supplied name.
		if (name == null && !item.contains(":")) name = item.toLowerCase().replace('_', ' ');
		List<String> lore = branchSection.getStringList("Lore");
		if (lore.isEmpty()) lore = defaultLore;

		boolean enchanted = branchSection.getBoolean("Enchanted", defaultEnchanted);
		boolean draggable = branchSection.getBoolean("Draggable", defaultDraggable);

		Object defaultCmd      = defaultData != null ? defaultData.get("customModelData") : null;
		int    defaultCmdValue = defaultCmd instanceof Integer cmd ? cmd : 0;
		int    customModelData = branchSection.getInt("Custom_Model_Data", defaultCmdValue);
		if (customModelData > 0) itemData.put("customModelData", customModelData);

		ItemBuilder itemBuilder = SlotItemFactory.create(itemResolver, item, name, itemData, lore, enchanted);

		ConditionalSlotData nestedData      = null;
		var                 nestedCondition = branchSection.getConfigurationSection("Condition");
		if (nestedCondition != null) {
			nestedData = parse(itemResolver, nestedCondition, item, name, itemData, lore, enchanted, draggable);
		}

		var     clickAction      = parseClickAction(branchSection.getConfigurationSection("OnClick"));
		var     rightClickAction = parseClickAction(branchSection.getConfigurationSection("OnRightClick"));
		boolean hasAction        = clickAction != null || rightClickAction != null;

		return new ConditionalSlotData.BranchData(itemBuilder, name, lore, hasAction, draggable, clickAction,
		                                          rightClickAction, nestedData);
	}

	@Nullable
	static ConditionalSlotData.ClickAction parseClickAction(@Nullable ConfigurationSection section) {
		if (section == null) return null;

		String command = section.getString("Command");
		if (command != null) return new ConditionalSlotData.CommandAction(command);

		if (section.isString("Inventory")) {
			return new ConditionalSlotData.InventoryAction(section.getString("Inventory"));
		}

		if (section.isConfigurationSection("Inventory")) {
			var invSection = section.getConfigurationSection("Inventory");
			if (invSection == null) return null;

			if ("anvil".equalsIgnoreCase(invSection.getString("Type"))) {
				String title          = invSection.getString("Title", "Enter Text");
				String text           = invSection.getString("Text", "");
				var    successSection = invSection.getConfigurationSection("Success");
				String successCommand = successSection != null ? successSection.getString("Command") : null;
				return new ConditionalSlotData.AnvilAction(title, text, successCommand);
			}
		}

		return null;
	}

}
