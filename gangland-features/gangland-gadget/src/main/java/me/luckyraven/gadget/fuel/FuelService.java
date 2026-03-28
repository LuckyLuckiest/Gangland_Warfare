package me.luckyraven.gadget.fuel;

import me.luckyraven.item.fuel.Fuel;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central runtime API for fuel operations. Manages a registry of {@link Fuel} definitions and provides methods to
 * consume, add, and query fuel from items in a player's inventory.
 *
 * <p>Fuel definitions are registered during initialization (typically by {@code UniqueItemAddon}).
 * Gadgets (cars, jetpacks, etc.) reference a fuel key to specify which fuel type they consume.
 */
public class FuelService {

	private final Map<String, Fuel> fuelRegistry = new ConcurrentHashMap<>();

	/**
	 * Cache of the last-known inventory slot containing a fuel item per player, keyed by fuelKey. Used to avoid
	 * scanning the entire inventory on every tick.
	 */
	private final Map<UUID, Map<String, Integer>> slotCache = new ConcurrentHashMap<>();

	// =========================================================================
	// Registry
	// =========================================================================

	/**
	 * Registers a fuel definition. Called during config loading.
	 */
	public void registerFuel(Fuel fuel) {
		fuelRegistry.put(fuel.getFuelKey(), fuel);
	}

	/**
	 * Returns the fuel definition for the given key, or {@code null} if not registered.
	 */
	@Nullable
	public Fuel getFuel(String fuelKey) {
		return fuelRegistry.get(fuelKey);
	}

	/**
	 * Returns the first registered fuel definition whose {@code fuelMaterial} matches the given material, or
	 * {@code null} if none match.
	 */
	@Nullable
	public Fuel findFuelByMaterial(Material material) {
		for (Fuel fuel : fuelRegistry.values()) {
			Material fuelMat = fuel.getFuelMaterial().parseMaterial();
			if (fuelMat != null && fuelMat == material) return fuel;
		}
		return null;
	}

	// =========================================================================
	// Inventory operations
	// =========================================================================

	/**
	 * Finds the first fuel item of the given type in the player's inventory. Uses a cached slot hint to avoid full
	 * scans when possible.
	 *
	 * @return the slot index, or -1 if not found
	 */
	public int findFuelSlot(Player player, String fuelKey) {
		PlayerInventory inventory = player.getInventory();

		// Check cached slot first
		Map<String, Integer> playerCache = slotCache.get(player.getUniqueId());
		if (playerCache != null) {
			Integer cached = playerCache.get(fuelKey);
			if (cached != null && cached >= 0 && cached < inventory.getSize()) {
				ItemStack item = inventory.getItem(cached);
				if (item != null && fuelKey.equals(Fuel.getFuelKey(item))) {
					return cached;
				}
			}
		}

		// Full scan
		for (int i = 0; i < inventory.getSize(); i++) {
			ItemStack item = inventory.getItem(i);
			if (item != null && fuelKey.equals(Fuel.getFuelKey(item))) {
				cacheSlot(player.getUniqueId(), fuelKey, i);
				return i;
			}
		}

		return -1;
	}

	/**
	 * Finds the first fuel item of the given type in the player's inventory.
	 *
	 * @return the fuel ItemStack, or {@code null} if not found
	 */
	@Nullable
	public ItemStack findFuelItem(Player player, String fuelKey) {
		int slot = findFuelSlot(player, fuelKey);
		if (slot < 0) return null;
		return player.getInventory().getItem(slot);
	}

	/**
	 * Returns whether the player has a fuel item of the given type with remaining fuel.
	 */
	public boolean hasFuel(Player player, String fuelKey) {
		int slot = findFuelSlot(player, fuelKey);
		if (slot < 0) return false;
		ItemStack item = player.getInventory().getItem(slot);
		return item != null && Fuel.getCurrentFuel(item) > 0;
	}

	/**
	 * Returns the current fuel level of the first matching fuel item.
	 *
	 * @return the fuel level, or 0 if no matching fuel item is found
	 */
	public int getFuelLevel(Player player, String fuelKey) {
		ItemStack item = findFuelItem(player, fuelKey);
		if (item == null) return 0;
		return Fuel.getCurrentFuel(item);
	}

	/**
	 * Returns the maximum fuel capacity of the first matching fuel item.
	 *
	 * @return the max fuel, or 0 if no matching fuel item is found
	 */
	public int getMaxFuelLevel(Player player, String fuelKey) {
		ItemStack item = findFuelItem(player, fuelKey);
		if (item == null) return 0;
		return Fuel.getMaxFuel(item);
	}

	/**
	 * Consumes fuel from the first matching fuel item in the player's inventory. Updates the item's NBT in-place.
	 *
	 * @param player the player whose inventory to search
	 * @param fuelKey the fuel type to consume
	 * @param amount the amount of fuel ticks to consume
	 *
	 * @return {@code true} if fuel was available and consumed
	 */
	public boolean consumeFuel(Player player, String fuelKey, int amount) {
		int slot = findFuelSlot(player, fuelKey);
		if (slot < 0) return false;

		PlayerInventory inventory = player.getInventory();
		ItemStack       item      = inventory.getItem(slot);
		if (item == null) return false;

		int current = Fuel.getCurrentFuel(item);
		if (current <= 0) return false;

		int       newFuel = Math.max(0, current - amount);
		ItemStack updated = Fuel.setCurrentFuel(item, newFuel);
		inventory.setItem(slot, updated);
		return true;
	}

	/**
	 * Adds fuel to the first matching fuel item in the player's inventory. Caps at the item's maximum fuel capacity.
	 *
	 * @param player the player whose inventory to search
	 * @param fuelKey the fuel type to add
	 * @param amount the amount of fuel ticks to add
	 *
	 * @return {@code true} if a fuel item was found and fuel was added (not already full)
	 */
	public boolean addFuel(Player player, String fuelKey, int amount) {
		int slot = findFuelSlot(player, fuelKey);
		if (slot < 0) return false;

		PlayerInventory inventory = player.getInventory();
		ItemStack       item      = inventory.getItem(slot);
		if (item == null) return false;

		int current = Fuel.getCurrentFuel(item);
		int max     = Fuel.getMaxFuel(item);
		if (current >= max) return false;

		int       newFuel = Math.min(max, current + amount);
		ItemStack updated = Fuel.setCurrentFuel(item, newFuel);
		inventory.setItem(slot, updated);
		return true;
	}

	/**
	 * Clears the slot cache for a player. Called on disconnect.
	 */
	public void clearCache(UUID playerId) {
		slotCache.remove(playerId);
	}

	private void cacheSlot(UUID playerId, String fuelKey, int slot) {
		slotCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(fuelKey, slot);
	}

}
