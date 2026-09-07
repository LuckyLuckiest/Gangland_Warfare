package org.luckyraven.gangland.inventory.service;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.inventory.InventoryHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player open-inventory tracker. Constructed once as a bean by the host plugin and threaded into the listeners, the
 * user factory, and the {@link InventoryHandler} static init seam.
 */
public class InventoryRegistry {

	private final Map<UUID, Set<InventoryHandler>> playerInventories = new ConcurrentHashMap<>();

	public InventoryRegistry() { }

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

	@Nullable
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
