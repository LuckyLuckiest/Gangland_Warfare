package me.luckyraven.file.configuration.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.*;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.inventory.unique.UniqueItemHandler;
import me.luckyraven.item.ItemParser;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.util.Pair;
import me.luckyraven.util.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runtime half of the former static {@code InventoryAddon}: holds the service references that registration and
 * open-inventory logic need. Lives in the CONFIG phase because some of its dependencies (e.g. {@link UserManager},
 * {@link ItemSourceProvider}) are CONFIG-phase beans. Reads/writes the {@link InventoryDefinitionStore} that was
 * constructed in FILE phase.
 */
public class InventoryRuntimeContext {

	private final Gangland                 gangland;
	private final InventoryDefinitionStore definitionStore;
	private final ItemSourceProvider       itemSourceProvider;
	private final ConditionEvaluator       conditionEvaluator;
	private final UserManager<Player>      userManager;
	private final PermissionManager        permissionManager;
	private final PlaceholderService       placeholderService;
	private final ItemParser               itemParser;

	public InventoryRuntimeContext(Gangland gangland,
	                               InventoryDefinitionStore definitionStore,
	                               ItemSourceProvider itemSourceProvider,
	                               ConditionEvaluator conditionEvaluator,
	                               UserManager<Player> userManager,
	                               PermissionManager permissionManager,
	                               PlaceholderService placeholderService,
	                               ItemParser itemParser) {
		this.gangland           = gangland;
		this.definitionStore    = definitionStore;
		this.itemSourceProvider = itemSourceProvider;
		this.conditionEvaluator = conditionEvaluator;
		this.userManager        = userManager;
		this.permissionManager  = permissionManager;
		this.placeholderService = placeholderService;
		this.itemParser         = itemParser;
	}

	public InventoryDefinitionStore definitionStore() {
		return definitionStore;
	}

	/**
	 * Resolver passed into {@code SlotItemFactory.create(...)} so the {@code inventory-api} module can translate
	 * prefixed item refs (weapon:awp, …) without needing to compile-depend on {@code gangland-item}.
	 */
	public Function<String, ItemStack> itemResolver() {
		return itemParser::parse;
	}

	public void registerInventory(FileHandler fileHandler) {
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
			InventoryParser.configureSlots(this, InventoryHandler.factorOfNine(size), slotsSection.getName(), config,
			                               slots);
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
			InventoryParser.configureMultiInventory(this, config, informationSection, inventoryData);
		}

		for (Pair<State, String> state : states) {
			inventoryData.addOpenInventory(new OpenInventory(state.first(), state.second(), openPermission));
		}

		definitionStore.inventories().put(name, new InventoryBuilder(inventoryData, permission));
	}

	public void openInventoryForPlayer(Player player, String inventoryName) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		InventoryHandler existing = user.getInventory(inventoryName);
		if (existing != null) {
			existing.open(player);
			return;
		}

		InventoryBuilder invBuilder = definitionStore.inventories().get(inventoryName);
		if (invBuilder == null) return;

		if (invBuilder.permission() != null && !player.hasPermission(invBuilder.permission())) return;

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
		Fill line = new Fill(Settings.getInventoryLineName(), Settings.getInventoryLineItem());

		InventoryOpener opener      = this::openInventoryForPlayer;
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

	Gangland gangland() {
		return gangland;
	}

	private void registerUniqueItemHandler(String inventoryName, ConfigurationSection informationSection,
	                                       @Nullable String openPermission) {
		ConfigurationSection eventSection = informationSection.getConfigurationSection("Open.Event");
		if (eventSection == null || !eventSection.contains("OnItemClick")) return;

		String uniqueItemKey = eventSection.getString("UniqueItem");
		if (uniqueItemKey == null) return;

		var allowedActions = InventoryParser.parseActions(eventSection);
		definitionStore.uniqueItemHandlers().put(uniqueItemKey,
		                                         new UniqueItemHandler(inventoryName, uniqueItemKey, allowedActions,
		                                                               openPermission));
	}
}
