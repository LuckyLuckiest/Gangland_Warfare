package me.luckyraven.file.configuration.inventory;

import me.luckyraven.inventory.InventoryBuilder;
import me.luckyraven.inventory.handler.*;
import me.luckyraven.inventory.unique.UniqueItemHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure-data half of the former static {@code InventoryAddon}: holds the five lookup maps populated once at
 * construction. No external dependencies, lives in the FILE phase. Runtime services that operate over this data live on
 * {@link InventoryRuntimeContext}.
 */
public class InventoryDefinitionStore {

	private final Map<String, InventoryBuilder>                inventories       = new HashMap<>();
	private final Map<String, Class<? extends PlayerEvent>>    playerEvents      = new HashMap<>();
	private final Map<String, Class<? extends InventoryEvent>> inventoryEvents   = new HashMap<>();
	private final Map<String, UniqueItemHandler>               uniqueItemHandler = new HashMap<>();
	private final Map<Class<?>, SlotEventHandler>              slotHandlers      = new HashMap<>();

	public InventoryDefinitionStore() {
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

	public Map<String, InventoryBuilder> inventories() {
		return inventories;
	}

	public Map<String, Class<? extends PlayerEvent>> playerEvents() {
		return playerEvents;
	}

	public Map<String, Class<? extends InventoryEvent>> inventoryEvents() {
		return inventoryEvents;
	}

	public Map<String, UniqueItemHandler> uniqueItemHandlers() {
		return uniqueItemHandler;
	}

	public Map<Class<?>, SlotEventHandler> slotHandlers() {
		return slotHandlers;
	}

	@Nullable
	public InventoryBuilder getInventory(String key) {
		return inventories.get(key);
	}

	public Set<String> getInventoryKeys() {
		return inventories.keySet();
	}

	public int size() {
		return inventories.size();
	}

	@Nullable
	public UniqueItemHandler getUniqueItemHandler(String uniqueItemKey) {
		return uniqueItemHandler.get(uniqueItemKey);
	}
}
