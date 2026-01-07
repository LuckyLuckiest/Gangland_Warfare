package me.luckyraven.file.configuration.inventory;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.itemsource.GangItemSourceProvider;
import me.luckyraven.inventory.*;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.condition.ConditionalSlotData;
import me.luckyraven.inventory.condition.SlotCondition;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.inventory.unique.UniqueItemHandler;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Pair;
import me.luckyraven.util.Placeholder;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
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

	private static final Map<String, InventoryBuilder>                inventories       = new HashMap<>();
	private static final Map<String, Class<? extends PlayerEvent>>    playerEvents      = new HashMap<>();
	private static final Map<String, Class<? extends InventoryEvent>> inventoryEvents   = new HashMap<>();
	private static final Map<String, UniqueItemHandler>               uniqueItemHandler = new HashMap<>();

	private static ItemSourceProvider itemSourceProvider;
	private static ConditionEvaluator conditionEvaluator;

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

	public static void setConditionEvaluator(ConditionEvaluator conditionEvaluator) {
		InventoryAddon.conditionEvaluator = conditionEvaluator;
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

	@Nullable
	public static UniqueItemHandler getUniqueItemHandler(String uniqueItemKey) {
		return uniqueItemHandler.get(uniqueItemKey);
	}

	public static void registerInventory(Gangland gangland, FileHandler fileHandler) {
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            fileName = fileHandler.getName().toLowerCase();

		String configVersion = config.getString("Config_Version");
		if (configVersion != null) {
			// recreates the file if needed
			return;
		}

		var informationSection = config.getConfigurationSection("Information");
		Objects.requireNonNull(informationSection);

		var slotsSection = config.getConfigurationSection("Slots");

		// information section
		String name = informationSection.getString("Name");

		if (name == null || name.isEmpty()) name = fileName;

		String displayName        = informationSection.getString("Display_Name");
		String displayNameNonNull = Objects.requireNonNull(displayName);
		int    size               = informationSection.getInt("Size");
		String permission         = informationSection.getString("Permission");

		// the type would determine if it was an inventory handler or multi inventory
		// nothing more since it is exhausting
		String informationType = informationSection.getString("Type");
		String type            = Objects.requireNonNull(informationType);
		String tempOpenCommand = informationSection.getString("Open.Command");
		String openCommand     = null;

		if (tempOpenCommand != null) {
			openCommand = tempOpenCommand.startsWith("/") ? tempOpenCommand : "/" + tempOpenCommand;
			openCommand = openCommand.strip();
		}

		String openEvent      = informationSection.getString("Open.Event");
		String openPermission = informationSection.getString("Open.Permission");

		if (permission != null) gangland.getInitializer().getPermissionManager().addPermission(permission);
		int realSize = InventoryHandler.factorOfNine(size);

		List<Slot> slots = new ArrayList<>();

		// slots section
		if (slotsSection != null) configureSlots(gangland, realSize, slotsSection.getName(), config, slots);

		var configurationSection = informationSection.getConfigurationSection("Configuration");
		Objects.requireNonNull(configurationSection);

		boolean       configFill       = configurationSection.getBoolean("Fill");
		boolean       configBorder     = configurationSection.getBoolean("Border");
		List<Integer> configVertical   = configurationSection.getIntegerList("Line.Vertical");
		List<Integer> configHorizontal = configurationSection.getIntegerList("Line.Horizontal");

		// open the inventory according to these states
		List<Pair<State, String>> states = new ArrayList<>();

		if (openCommand != null) {
			states.add(new Pair<>(State.COMMAND, openCommand));
		}

		if (openEvent != null) {
			states.add(new Pair<>(State.EVENT, openEvent));

			// register unique item handler
			ConfigurationSection eventSection = informationSection.getConfigurationSection("Open.Event");

			if (eventSection != null && eventSection.contains("OnItemClick")) {
				String uniqueItemKey = eventSection.getString("UniqueItem");

				if (uniqueItemKey != null) {
					var allowedActions = parseActions(eventSection);
					var uniqueItem     = new UniqueItemHandler(name, uniqueItemKey, allowedActions, openPermission);

					uniqueItemHandler.put(uniqueItemKey, uniqueItem);
				}
			}
		}

		InventoryData inventoryData = new InventoryData(name, displayNameNonNull, type, size);
		inventoryData.addAllSlots(slots);
		inventoryData.setPermission(permission);
		inventoryData.setVerticalLine(configVertical);
		inventoryData.setHorizontalLine(configHorizontal);
		inventoryData.setFill(configFill);
		inventoryData.setBorder(configBorder);

		// Handle multi-inventory configuration
		if (type.equalsIgnoreCase("multi-inventory")) {
			configureMultiInventory(gangland, config, informationSection, inventoryData);
		}

		if (!states.isEmpty()) {
			for (Pair<State, String> state : states) {
				var openInventory = new OpenInventory(state.first(), state.second(), openPermission);

				inventoryData.addOpenInventory(openInventory);
			}
		}

		inventories.put(name, new InventoryBuilder(inventoryData, permission));
	}

	public static void openInventoryForPlayer(Gangland gangland, Player player, String inventoryName) {
		User<Player> user = gangland.getInitializer().getUserManager().getUser(player);

		// Check if user already has this inventory open
		InventoryHandler existingInventory = user.getInventory(inventoryName);
		if (existingInventory != null) {
			existingInventory.open(player);
			return;
		}

		// Get the inventory builder
		InventoryBuilder invBuilder = inventories.get(inventoryName);
		if (invBuilder == null) return;

		// Check permission
		if (invBuilder.permission() != null && !player.hasPermission(invBuilder.permission())) {
			return;
		}

		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());
		Fill line = new Fill(SettingAddon.getInventoryLineName(), SettingAddon.getInventoryLineItem());

		// Create the opener callback
		InventoryOpener opener      = (p, invName) -> openInventoryForPlayer(gangland, p, invName);
		Placeholder     placeholder = gangland.getInitializer().getPlaceholderService();

		// Check if it's a multi-inventory
		if (invBuilder.inventoryData().isMultiInventory()) {
			String          itemSource = invBuilder.inventoryData().getItemSource();
			List<ItemStack> items      = itemSourceProvider.getItems(player, itemSource);

			ButtonTags buttonTags = new ButtonTags(SettingAddon.getPreviousPage(), SettingAddon.getHomePage(),
												   SettingAddon.getNextPage());

			MultiInventory multiInventory = invBuilder.createMultiInventory(gangland, placeholder, player, items,
																			buttonTags, fill);

			if (multiInventory != null) {
				multiInventory.open(player);
				user.addInventory(multiInventory);
			}
		} else {
			InventoryHandler handler = invBuilder.createInventory(gangland, placeholder, user.getUser(), fill, line,
																  conditionEvaluator, opener);
			handler.open(player);
			user.addInventory(handler);
		}
	}

	private static List<Action> parseActions(ConfigurationSection eventSection) {
		List<Action> actions     = new ArrayList<>();
		Object       actionValue = eventSection.get("Action");

		if (actionValue instanceof String actionStr) {
			try {
				actions.add(Action.valueOf(actionStr.toUpperCase()));
			} catch (IllegalArgumentException ignored) { }
		} else if (actionValue instanceof List<?> actionList) {
			for (Object action : actionList) {
				if (!(action instanceof String actionStr)) continue;

				try {
					actions.add(Action.valueOf(actionStr.toUpperCase()));
				} catch (IllegalArgumentException ignored) { }
			}
		}

		if (actions.isEmpty()) {
			actions.add(Action.RIGHT_CLICK_AIR);
			actions.add(Action.RIGHT_CLICK_BLOCK);
		}

		return actions;
	}

	private static void configureMultiInventory(Gangland gangland, FileConfiguration config,
												ConfigurationSection information, InventoryData inventoryData) {
		String itemSource = information.getString("Multi.Item_Source");
		int    perPage    = information.getInt("Multi.Per_Page", 28);

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
		if (!slotsStr.endsWith(".")) slotsStr += ".";

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

			Slot slotObj;

			// Check if this slot has a condition
			ConfigurationSection conditionSection = section.getConfigurationSection("Condition");
			if (conditionSection != null) {
				var conditionalData = parseConditionalData(gangland, conditionSection, item, itemName, data, lore,
														   enchanted, draggable);
				slotObj = new Slot(slot, true, draggable, null);

				slotObj.setConditionalData(conditionalData);
			} else {
				slotObj = processEventItems(gangland, "Slots", config, slot, item, itemName, data, lore, enchanted,
											draggable);
			}

			slots.add(slotObj);
		}
	}

	/**
	 * Recursively parses conditional data (True/False branches with nested conditions)
	 */
	private static ConditionalSlotData parseConditionalData(Gangland gangland, ConfigurationSection conditionSection,
															String defaultItem, String defaultName,
															Map<String, Object> defaultData, List<String> defaultLore,
															boolean defaultEnchanted, boolean defaultDraggable) {
		// Parse the condition Value expression
		String valueExpression = conditionSection.getString("Value");
		if (valueExpression == null || valueExpression.isEmpty()) {
			throw new IllegalArgumentException("Condition must have a Value");
		}

		SlotCondition condition = new SlotCondition(valueExpression);

		// Parse True branch
		var trueSection = conditionSection.getConfigurationSection("True");

		if (trueSection == null) {
			trueSection = conditionSection.getConfigurationSection("true");
		}

		var trueData = parseBranchData(gangland, trueSection, defaultItem, defaultName, defaultData, defaultLore,
									   defaultEnchanted, defaultDraggable);

		// Parse False branch
		var falseSection = conditionSection.getConfigurationSection("False");

		if (falseSection == null) {
			falseSection = conditionSection.getConfigurationSection("false");
		}

		var falseData = parseBranchData(gangland, falseSection, defaultItem, defaultName, defaultData, defaultLore,
										defaultEnchanted, defaultDraggable);

		return new ConditionalSlotData(condition, trueData, falseData);
	}

	/**
	 * Parses a branch (True or False) which may contain nested conditions
	 */
	private static ConditionalSlotData.BranchData parseBranchData(Gangland gangland, ConfigurationSection branchSection,
																  String defaultItem, String defaultName,
																  Map<String, Object> defaultData,
																  List<String> defaultLore, boolean defaultEnchanted,
																  boolean defaultDraggable) {
		if (branchSection == null) {
			// Return default data
			ItemBuilder defaultBuilder = createItem(defaultItem, defaultName, defaultData, defaultLore,
													defaultEnchanted);
			return new ConditionalSlotData.BranchData(defaultBuilder, defaultName, defaultLore, false, defaultDraggable,
													  null, null, null);
		}

		// Parse item info for this branch first
		Map<String, Object> itemData = new HashMap<>();
		String              item     = getItemInfo(branchSection, itemData);
		if (item == null) item = defaultItem;

		String       name = branchSection.getString("Name", defaultName);
		List<String> lore = branchSection.getStringList("Lore");
		if (lore.isEmpty()) lore = defaultLore;

		boolean enchanted = branchSection.getBoolean("Enchanted", defaultEnchanted);
		boolean draggable = branchSection.getBoolean("Draggable", defaultDraggable);

		ItemBuilder itemBuilder = createItem(item, name, itemData, lore, enchanted);

		// Check for nested condition
		ConfigurationSection nestedCondition = branchSection.getConfigurationSection("Condition");
		ConditionalSlotData  nestedData      = null;
		if (nestedCondition != null) {
			// Use the current branch's parsed values as defaults for nested condition
			nestedData = parseConditionalData(gangland, nestedCondition, item, name, itemData, lore, enchanted,
											  draggable);
		}

		// Parse click action (left click / default)
		ConfigurationSection            onClickSection = branchSection.getConfigurationSection("OnClick");
		ConditionalSlotData.ClickAction clickAction    = parseClickAction(onClickSection);

		// Parse right click action
		ConfigurationSection            onRightClickSection = branchSection.getConfigurationSection("OnRightClick");
		ConditionalSlotData.ClickAction rightClickAction    = parseClickAction(onRightClickSection);

		boolean hasClickAction = clickAction != null || rightClickAction != null;

		return new ConditionalSlotData.BranchData(itemBuilder, name, lore, hasClickAction, draggable, clickAction,
												  rightClickAction, nestedData);
	}

	/**
	 * Parses click actions (Command, Inventory, or Anvil)
	 */
	@Nullable
	private static ConditionalSlotData.ClickAction parseClickAction(ConfigurationSection onClickSection) {
		if (onClickSection == null) return null;

		// Check for Command
		String command = onClickSection.getString("Command");
		if (command != null) {
			return new ConditionalSlotData.CommandAction(command);
		}

		// Check for Inventory
		if (onClickSection.isString("Inventory")) {
			String inventoryName = onClickSection.getString("Inventory");
			return new ConditionalSlotData.InventoryAction(inventoryName);
		} else if (onClickSection.isConfigurationSection("Inventory")) {
			ConfigurationSection invSection = onClickSection.getConfigurationSection("Inventory");
			if (invSection == null) return null;

			String type = invSection.getString("Type");
			if ("anvil".equalsIgnoreCase(type)) {
				String title = invSection.getString("Title", "Enter Text");
				String text  = invSection.getString("Text", "");

				var successSection = invSection.getConfigurationSection("Success");
				var successCommand = successSection != null ? successSection.getString("Command") : null;

				return new ConditionalSlotData.AnvilAction(title, text, successCommand);
			}
		}

		return null;
	}

	private static Slot processEventItems(Gangland gangland, String basePath, FileConfiguration config, int slotLoc,
										  String item, String itemName, Map<String, Object> data, List<String> lore,
										  boolean enchanted, boolean draggable) {
		String slotsBase = basePath + "." + slotLoc + ".";

		// Check for OnRightClick event
		String rightClickPath    = slotsBase + "OnRightClick";
		var    rightClickSection = config.getConfigurationSection(rightClickPath);

		for (String event : inventoryEvents.keySet()) {
			String eventSectionString = slotsBase + event + ".";
			String substring          = eventSectionString.substring(0, eventSectionString.length() - 1);
			var    eventSection       = config.getConfigurationSection(substring);
			if (eventSection == null) continue;

			// so far, there is support for clickable events
			if (inventoryEvents.get(event).equals(InventoryClickEvent.class)) {
				return inventoryClickEvent(gangland, eventSection, rightClickSection, slotLoc, item, itemName, data,
										   lore, enchanted, draggable);
			} else if (inventoryEvents.get(event).equals(InventoryInteractEvent.class)) {

			} else if (inventoryEvents.get(event).equals(InventoryCloseEvent.class)) {

			} else if (inventoryEvents.get(event).equals(InventoryEvent.class)) {

			}
		}

		// Check if only right-click is defined (no OnClick)
		if (rightClickSection != null) {
			return inventoryRightClickOnly(gangland, rightClickSection, slotLoc, item, itemName, data, lore, enchanted,
										   draggable);
		}

		// add the slot if it doesn't contain an event
		ItemBuilder itemBuilder = createItem(item, itemName, data, lore, enchanted);

		return new Slot(slotLoc, false, draggable, itemBuilder);
	}

	private static Slot inventoryClickEvent(Gangland gangland, ConfigurationSection eventSection,
											@Nullable ConfigurationSection rightClickSection, int slotLoc, String item,
											String itemName, Map<String, Object> data, List<String> lore,
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
				openInventoryForPlayer(gangland, player, slotInventory);
			}
		});

		// Handle right click action if defined
		if (rightClickSection != null) {
			String rightCommand    = rightClickSection.getString("Command");
			String rightInventory  = rightClickSection.getString("Inventory");
			String rightPermission = rightClickSection.getString("Permission");

			slot.setRightClickable((player, inventoryHandler, builder) -> {
				if (rightPermission != null && !player.hasPermission(rightPermission)) return;

				if (rightCommand != null) {
					String command = rightCommand.startsWith("/") ? rightCommand.substring(1) : rightCommand;
					player.performCommand(command);
				}

				if (rightInventory != null) {
					openInventoryForPlayer(gangland, player, rightInventory);
				}
			});
		}

		return slot;
	}

	private static Slot inventoryRightClickOnly(Gangland gangland, ConfigurationSection rightClickSection, int slotLoc,
												String item, String itemName, Map<String, Object> data,
												List<String> lore, boolean enchanted, boolean draggable) {
		String rightCommand    = rightClickSection.getString("Command");
		String rightInventory  = rightClickSection.getString("Inventory");
		String rightPermission = rightClickSection.getString("Permission");

		ItemBuilder itemBuilder = createItem(item, itemName, data, lore, enchanted);

		Slot slot = new Slot(slotLoc, true, draggable, itemBuilder);

		// Empty left click handler
		slot.setClickable((player, inventoryHandler, builder) -> { });

		slot.setRightClickable((player, inventoryHandler, builder) -> {
			if (rightPermission != null && !player.hasPermission(rightPermission)) return;

			if (rightCommand != null) {
				String command = rightCommand.startsWith("/") ? rightCommand.substring(1) : rightCommand;
				player.performCommand(command);
			}

			if (rightInventory != null) {
				openInventoryForPlayer(gangland, player, rightInventory);
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
