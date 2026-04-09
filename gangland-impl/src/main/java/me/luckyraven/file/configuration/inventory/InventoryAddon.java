package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.*;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.handler.*;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.inventory.unique.UniqueItemHandler;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.util.Pair;
import me.luckyraven.util.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryAddon {

	// Package-private so InventoryParser can access them directly
	static final Map<String, InventoryBuilder>                inventories       = new HashMap<>();
	static final Map<String, Class<? extends PlayerEvent>>    playerEvents      = new HashMap<>();
	static final Map<String, Class<? extends InventoryEvent>> inventoryEvents   = new HashMap<>();
	static final Map<String, UniqueItemHandler>               uniqueItemHandler = new HashMap<>();
	static final Map<Class<?>, SlotEventHandler>              slotHandlers      = new HashMap<>();

	private static ItemSourceProvider  itemSourceProvider;
	private static ConditionEvaluator  conditionEvaluator;
	private static UserManager<Player> userManager;
	private static PermissionManager   permissionManager;
	private static PlaceholderService  placeholderService;

	static {
		// Player events
		playerEvents.put("OnItemClick", PlayerInteractEvent.class);
		playerEvents.put("OnDrop", PlayerDropItemEvent.class);
		playerEvents.put("OnSwapHand", PlayerSwapHandItemsEvent.class);
		playerEvents.put("OnJoin", PlayerJoinEvent.class);
		playerEvents.put("OnQuit", PlayerQuitEvent.class);

		// Inventory events
		inventoryEvents.put("OnClick", InventoryClickEvent.class);
		inventoryEvents.put("OnInteract", InventoryInteractEvent.class);
		inventoryEvents.put("OnClose", InventoryCloseEvent.class);
		inventoryEvents.put("OnInventory", InventoryEvent.class);

		// Slot handlers — one stateless instance per event type
		SlotEventHandler clickHandler = new ClickSlotHandler();
		slotHandlers.put(InventoryClickEvent.class, clickHandler);
		slotHandlers.put(InventoryInteractEvent.class, clickHandler);
		slotHandlers.put(InventoryEvent.class, clickHandler);
		slotHandlers.put(InventoryCloseEvent.class, new CloseSlotHandler());

		SlotEventHandler commandHandler = new PlayerInteractSlotHandler();
		slotHandlers.put(PlayerInteractEvent.class, commandHandler);
		slotHandlers.put(PlayerDropItemEvent.class, new DropSlotHandler());
		slotHandlers.put(PlayerSwapHandItemsEvent.class, new SwapHandSlotHandler());
		slotHandlers.put(PlayerJoinEvent.class, new JoinSlotHandler());
		slotHandlers.put(PlayerQuitEvent.class, new QuitSlotHandler());
	}

	public static void setItemSourceProvider(ItemSourceProvider provider) {
		itemSourceProvider = provider;
	}

	public static void setConditionEvaluator(ConditionEvaluator conditionEvaluator) {
		InventoryAddon.conditionEvaluator = conditionEvaluator;
	}

	public static void setUserManager(UserManager<Player> userManager) {
		InventoryAddon.userManager = userManager;
	}

	public static void setPermissionManager(PermissionManager permissionManager) {
		InventoryAddon.permissionManager = permissionManager;
	}

	public static void setPlaceholderService(PlaceholderService placeholderService) {
		InventoryAddon.placeholderService = placeholderService;
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
		var config   = fileHandler.getFileConfiguration();
		var fileName = fileHandler.getName().toLowerCase();

		if (config.getString("Config_Version") != null) return;

		var informationSection = config.getConfigurationSection("Information");
		Objects.requireNonNull(informationSection);

		String name = informationSection.getString("Name");
		if (name == null || name.isEmpty()) name = fileName;

		String displayName = Objects.requireNonNull(informationSection.getString("Display_Name"));
		int    size        = informationSection.getInt("Size");
		String permission  = informationSection.getString("Permission");
		String type        = Objects.requireNonNull(informationSection.getString("Type"));

		String tempOpenCommand = informationSection.getString("Open.Command");
		String openCommand     = null;
		if (tempOpenCommand != null) {
			openCommand = (tempOpenCommand.startsWith("/") ? tempOpenCommand : "/" + tempOpenCommand).strip();
		}

		String openEvent      = informationSection.getString("Open.Event");
		String openPermission = informationSection.getString("Open.Permission");

		if (permission != null) permissionManager.addPermission(permission);

		List<Slot> slots        = new ArrayList<>();
		var        slotsSection = config.getConfigurationSection("Slots");
		if (slotsSection != null) {
			InventoryParser.configureSlots(gangland, InventoryHandler.factorOfNine(size), slotsSection.getName(),
			                               config, slots);
		}

		var configSection = Objects.requireNonNull(
				informationSection.getConfigurationSection("Configuration"));
		boolean       fill       = configSection.getBoolean("Fill");
		boolean       border     = configSection.getBoolean("Border");
		List<Integer> vertical   = configSection.getIntegerList("Line.Vertical");
		List<Integer> horizontal = configSection.getIntegerList("Line.Horizontal");

		List<Pair<State, String>> states = new ArrayList<>();
		if (openCommand != null) states.add(new Pair<>(State.COMMAND, openCommand));

		if (openEvent != null) {
			states.add(new Pair<>(State.EVENT, openEvent));
			registerUniqueItemHandler(name, informationSection, openPermission);
		}

		InventoryData inventoryData = new InventoryData(name, displayName, type, size);
		inventoryData.addAllSlots(slots);
		inventoryData.setPermission(permission);
		inventoryData.setVerticalLine(vertical);
		inventoryData.setHorizontalLine(horizontal);
		inventoryData.setFill(fill);
		inventoryData.setBorder(border);

		if (type.equalsIgnoreCase("multi-inventory")) {
			InventoryParser.configureMultiInventory(gangland, config, informationSection, inventoryData);
		}

		for (Pair<State, String> state : states) {
			inventoryData.addOpenInventory(new OpenInventory(state.first(), state.second(), openPermission));
		}

		inventories.put(name, new InventoryBuilder(inventoryData, permission));
	}

	public static void openInventoryForPlayer(Gangland gangland, Player player, String inventoryName) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		InventoryHandler existing = user.getInventory(inventoryName);
		if (existing != null) {
			existing.open(player);
			return;
		}

		InventoryBuilder invBuilder = inventories.get(inventoryName);
		if (invBuilder == null) return;

		if (invBuilder.permission() != null && !player.hasPermission(invBuilder.permission())) return;

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
		Fill line = new Fill(Settings.getInventoryLineName(), Settings.getInventoryLineItem());

		InventoryOpener opener      = (p, invName) -> openInventoryForPlayer(gangland, p, invName);
		Placeholder     placeholder = placeholderService;

		if (invBuilder.inventoryData().isMultiInventory()) {
			String          itemSource = invBuilder.inventoryData().getItemSource();
			List<ItemStack> items      = itemSourceProvider.getItems(player, itemSource);
			ButtonTags buttonTags = new ButtonTags(Settings.getPreviousPage(), Settings.getHomePage(),
			                                       Settings.getNextPage());
			MultiInventory multi = invBuilder.createMultiInventory(gangland, placeholder, player, items, buttonTags,
			                                                       fill);
			if (multi != null) {
				multi.open(player);
				user.addInventory(multi);
			}
		} else {
			InventoryHandler handler = invBuilder.createInventory(gangland, placeholder, user.getUser(), fill, line,
			                                                      conditionEvaluator, opener);
			handler.open(player);
			user.addInventory(handler);
		}
	}

	private static void registerUniqueItemHandler(String inventoryName, ConfigurationSection informationSection,
	                                              @Nullable String openPermission) {
		ConfigurationSection eventSection = informationSection.getConfigurationSection("Open.Event");
		if (eventSection == null || !eventSection.contains("OnItemClick")) return;

		String uniqueItemKey = eventSection.getString("UniqueItem");
		if (uniqueItemKey == null) return;

		var allowedActions = InventoryParser.parseActions(eventSection);
		uniqueItemHandler.put(uniqueItemKey,
		                      new UniqueItemHandler(inventoryName, uniqueItemKey, allowedActions, openPermission));
	}
}
