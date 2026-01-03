package me.luckyraven.loot.data;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.inventory.InventoryHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents an active loot chest session (player has the chest open)
 */
@Getter
public class LootChestSession {

	private final UUID             sessionId;
	private final Player           player;
	private final LootChestData    chestData;
	private final InventoryHandler inventory;
	private final List<ItemStack>  generatedLoot;

	// Slot mapping: maps inventory slot -> generated loot index
	private int[] slotMapping;

	private SessionState state;
	private boolean      itemTaken;

	// Cracking minigame state
	@Setter
	private boolean crackingRequired;
	@Setter
	private boolean crackingCompleted;
	@Setter
	private long    crackingStartTime;

	public LootChestSession(Player player, LootChestData chestData, InventoryHandler inventory,
							List<ItemStack> generatedLoot) {
		this.sessionId     = UUID.randomUUID();
		this.player        = player;
		this.chestData     = chestData;
		this.inventory     = inventory;
		this.generatedLoot = generatedLoot;
		this.state         = SessionState.OPEN;
		this.itemTaken     = false;

		this.crackingRequired  = false;
		this.crackingCompleted = false;
	}

	/**
	 * Opens the chest immediately and populates with loot
	 */
	public void open() {
		populateInventory();
		inventory.open(player);
		state = SessionState.LOOTING;
	}

	/**
	 * Marks that the player has taken an item from the chest
	 */
	public void markItemTaken() {
		this.itemTaken = true;
	}

	/**
	 * Checks if any item was taken from the chest
	 */
	public boolean hasItemBeenTaken() {
		return itemTaken;
	}

	public void cancel() {
		state = SessionState.CANCELLED;
	}

	public void close() {
		state = SessionState.CLOSED;

		// Sync the current inventory state back to chest data
		syncInventoryToChestData();

		// Only mark as looted if an item was actually taken
		if (!itemTaken) return;

		chestData.markAsLooted();
	}

	/**
	 * Syncs the current inventory state back to the chest data for persistence
	 */
	private void syncInventoryToChestData() {
		List<ItemStack> currentItems = new ArrayList<>();
		int[]           mapping      = new int[inventory.getSize()];
		Arrays.fill(mapping, -1);

		for (int i = 0; i < inventory.getSize(); i++) {
			ItemStack item = inventory.getInventory().getItem(i);

			if (!(item != null && !item.getType().isAir())) continue;

			currentItems.add(item.clone());
			mapping[i] = currentItems.size() - 1;
		}

		chestData.setCurrentInventory(currentItems.isEmpty() ? null : currentItems);
		chestData.setCurrentSlotMapping(currentItems.isEmpty() ? null : mapping);
	}

	private void populateInventory() {
		// Check if chest has existing inventory (items from previous session)
		if (chestData.getCurrentInventory() != null && chestData.getCurrentSlotMapping() != null) {
			restoreExistingInventory();
			return;
		}

		// Generate new random slot placement
		populateWithRandomSlots();
	}

	/**
	 * Restores the inventory from the chest's persisted state
	 */
	private void restoreExistingInventory() {
		List<ItemStack> existingItems = chestData.getCurrentInventory();
		int[]           mapping       = chestData.getCurrentSlotMapping();

		this.slotMapping = mapping.clone();

		for (int slot = 0; slot < mapping.length && slot < inventory.getSize(); slot++) {
			int itemIndex = mapping[slot];

			if (!(itemIndex >= 0 && itemIndex < existingItems.size())) continue;

			ItemStack item = existingItems.get(itemIndex);

			if (item == null) continue;

			inventory.setItem(slot, item.clone(), true);
		}
	}

	/**
	 * Populates inventory with items at random slots
	 */
	private void populateWithRandomSlots() {
		if (generatedLoot == null || generatedLoot.isEmpty()) return;

		int inventorySize = inventory.getSize();

		// Create a list of available slots
		List<Integer> availableSlots = new ArrayList<>();

		for (int i = 0; i < inventorySize; i++) {
			availableSlots.add(i);
		}

		// Shuffle to get random slots
		Collections.shuffle(availableSlots, new Random());

		// Initialize slot mapping
		this.slotMapping = new int[inventorySize];
		Arrays.fill(slotMapping, -1);

		int itemIndex = 0;
		for (ItemStack item : generatedLoot) {
			if (item == null) continue;
			if (itemIndex >= availableSlots.size()) break;

			int slot = availableSlots.get(itemIndex);
			inventory.setItem(slot, item.clone(), true);
			slotMapping[slot] = itemIndex;
			itemIndex++;
		}

		// Save the initial state to chest data
		List<ItemStack> itemsCopy = new ArrayList<>();
		for (ItemStack item : generatedLoot) {
			itemsCopy.add(item != null ? item.clone() : null);
		}
		chestData.setCurrentInventory(itemsCopy);
		chestData.setCurrentSlotMapping(slotMapping.clone());
	}

	public enum SessionState {
		OPEN,
		CRACKING,
		LOOTING,
		CLOSED,
		CANCELLED
	}

}
