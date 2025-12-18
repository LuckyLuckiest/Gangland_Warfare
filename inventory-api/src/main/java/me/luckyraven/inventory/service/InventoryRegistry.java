package me.luckyraven.inventory.service;

import me.luckyraven.inventory.InventoryHandler;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryRegistry {

	private static final InventoryRegistry INSTANCE = new InventoryRegistry();

	private final Map<UUID, Set<InventoryHandler>> playerInventories = new ConcurrentHashMap<>();

	private InventoryRegistry() { }

	public static InventoryRegistry getInstance() {
		return INSTANCE;
	}

	public void registerInventory(UUID uuid, InventoryHandler inventoryHandler) {
		playerInventories.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(inventoryHandler);
	}

	public void unregisterInventory(UUID uuid, InventoryHandler inventoryHandler) {
		Set<InventoryHandler> inventories = playerInventories.get(uuid);

		if (inventories != null) {
			inventories.remove(inventoryHandler);
		}
	}

	public List<InventoryHandler> getInventories(UUID uuid) {
		return new ArrayList<>(playerInventories.getOrDefault(uuid, Collections.emptySet()));
	}

	public InventoryHandler findByInventory(Inventory inventory) {
		return playerInventories.values()
								.stream()
								.flatMap(Set::stream)
								.filter(handler -> inventory.equals(handler.getInventory()))
								.findFirst()
								.orElse(null);
	}

	public void clear(UUID uuid) {
		playerInventories.remove(uuid);
	}

}
