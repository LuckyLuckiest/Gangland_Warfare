package me.luckyraven.inventory.loot.data;

import lombok.Getter;
import me.luckyraven.inventory.InventoryHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

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

	private SessionState state;
	private boolean      itemTaken;

	public LootChestSession(JavaPlugin plugin, Player player, LootChestData chestData, InventoryHandler inventory,
							List<ItemStack> generatedLoot) {
		this.sessionId     = UUID.randomUUID();
		this.player        = player;
		this.chestData     = chestData;
		this.inventory     = inventory;
		this.generatedLoot = generatedLoot;
		this.state         = SessionState.OPEN;
		this.itemTaken     = false;
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

		// Only mark as looted if an item was actually taken
		if (!itemTaken) return;

		chestData.markAsLooted();
	}

	private void populateInventory() {
		int slot = 0;

		for (ItemStack item : generatedLoot) {
			if (slot >= inventory.getSize()) break;
			if (item == null) continue;

			inventory.setItem(slot++, item, true);
		}
	}

	public enum SessionState {
		OPEN,
		LOOTING,
		CLOSED,
		CANCELLED
	}

}
