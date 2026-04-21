package me.luckyraven.file.configuration.inventory;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.Pair;
import me.luckyraven.core.Placeholder;
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
import me.luckyraven.persistence.config.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Runtime half of the former static {@code InventoryAddon}: holds the service references that registration and
 * open-inventory logic need. Lives in the CONFIG phase because some of its dependencies (e.g. {@link UserManager},
 * {@link ItemSourceProvider}) are CONFIG-phase beans. Reads/writes the {@link InventoryDefinitionStore} that was
 * constructed in FILE phase.
 */
@CustomLog
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
		FileConfiguration config   = fileHandler.getFileConfiguration();
		String            fileName = fileHandler.getName().toLowerCase();

		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		if (root.get("Config_Version").asString().orNull() != null) return;

		MappingNode informationSection = root.get("Information").asMapping().required().orNull();
		if (informationSection == null) {
			if (!report.isEmpty()) report.log(log);
			return;
		}

		NodeReader information = NodeReader.of(informationSection, report);

		String name = information.get("Name").asString().orNull();
		if (name == null || name.isEmpty()) name = fileName;

		String displayName = information.get("Display_Name").asString().required().orDefault(name);
		int    size        = information.get("Size").asInt().min(9).required().orDefault(27);
		String permission  = information.get("Permission").asString().orNull();
		String type        = information.get("Type").asString().required().orDefault("single-inventory");

		// Information.Multi is consumed via the legacy Bukkit path inside
		// InventoryParser.configureMultiInventory (Multi.Item_Source / Multi.Per_Page).
		// Touch it here so the unknown-key sweep knows it's consumed.
		information.get("Multi");

		MappingNode openSection = information.get("Open").asMapping().orNull();
		NodeReader  open        = openSection != null ? NodeReader.of(openSection, report) : null;

		String tempOpenCommand = open == null ? null : open.get("Command").asString().orNull();
		String openCommand     = null;
		if (tempOpenCommand != null) {
			openCommand = (tempOpenCommand.startsWith("/") ? tempOpenCommand : "/" + tempOpenCommand).strip();
		}

		// Open.Event may be a scalar (event name) OR a mapping (structured event spec consumed by
		// registerUniqueItemHandler below). Route through NodeReader.get() so the key is marked touched either way —
		// we still only pull out the scalar form here; mapping shapes are later drilled via the Bukkit API.
		String openEvent = null;
		if (open != null) {
			ConfigNode eventNode = open.get("Event").node();
			if (eventNode instanceof ScalarNode scalar) openEvent = scalar.value();
		}

		String openPermission = open == null ? null : open.get("Permission").asString().orNull();

		// Open.Type is an admin-facing hint for inventory event state; consumed via the legacy Bukkit path further
		// below. Touching it here suppresses a spurious unknown_key sweep warning.
		if (open != null) open.get("Type");

		if (permission != null) permissionManager.addPermission(permission);

		// InventoryParser reads deeply-nested Slot sub-sections and passes ConfigurationSection into external
		// SlotEventHandler implementations; keeping it on the Bukkit path avoids a ripple through every handler.
		// Positional errors for the top-level Information/Configuration sections still surface via `report`.
		// Touch "Slots" on the root reader so the unknown-key sweep knows it's consumed (via the Bukkit path below).
		root.get("Slots");
		// Static_Items is consumed via the legacy Bukkit path inside
		// InventoryParser.configureMultiInventory. Touch here for the sweep.
		root.get("Static_Items");
		List<Slot> slots        = new ArrayList<>();
		var        slotsSection = config.getConfigurationSection("Slots");
		if (slotsSection != null) {
			InventoryParser.configureSlots(this, InventoryHandler.factorOfNine(size), slotsSection.getName(), config,
			                               slots);
		}

		MappingNode   configSection = information.get("Configuration").asMapping().required().orNull();
		boolean       fill          = false;
		boolean       border        = false;
		List<Integer> vertical      = List.of();
		List<Integer> horizontal    = List.of();

		if (configSection != null) {
			NodeReader configuration = NodeReader.of(configSection, report);
			fill   = configuration.get("Fill").asBool().orDefault(false);
			border = configuration.get("Border").asBool().orDefault(false);

			MappingNode lineSection = configuration.get("Line").asMapping().orNull();
			if (lineSection != null) {
				NodeReader line = NodeReader.of(lineSection, report);
				vertical   = line.get("Vertical").asList().ofInts().orEmpty();
				horizontal = line.get("Horizontal").asList().ofInts().orEmpty();
			}
		}

		List<Pair<State, String>> states = new ArrayList<>();
		if (openCommand != null) states.add(new Pair<>(State.COMMAND, openCommand));

		// Legacy non-positional path retained for registerUniqueItemHandler — it still drills into nested sections via
		// ConfigurationSection. Upstream errors on Information/Open.Event have already been recorded in `report`.
		ConfigurationSection informationSectionLegacy = config.getConfigurationSection("Information");
		if (openEvent != null && informationSectionLegacy != null) {
			states.add(new Pair<>(State.EVENT, openEvent));
			registerUniqueItemHandler(name, informationSectionLegacy, openPermission);
		}

		InventoryData inventoryData = new InventoryData(name, displayName, type, size);
		inventoryData.addAllSlots(slots);
		inventoryData.setPermission(permission);
		inventoryData.setVerticalLine(vertical);
		inventoryData.setHorizontalLine(horizontal);
		inventoryData.setFill(fill);
		inventoryData.setBorder(border);

		if (type.equalsIgnoreCase("multi-inventory") && informationSectionLegacy != null) {
			InventoryParser.configureMultiInventory(this, config, informationSectionLegacy, inventoryData);
		}

		for (Pair<State, String> state : states) {
			inventoryData.addOpenInventory(new OpenInventory(state.first(), state.second(), openPermission));
		}

		if (!report.isEmpty()) report.log(log);

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
