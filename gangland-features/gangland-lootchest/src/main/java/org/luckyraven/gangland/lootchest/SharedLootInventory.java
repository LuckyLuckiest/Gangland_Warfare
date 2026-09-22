package org.luckyraven.gangland.lootchest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.util.ChatUtil;

/**
 * Thin wrapper around a raw Bukkit {@link Inventory} for a shared loot chest: multiple players can have the same
 * chest open at once, and {@link org.luckyraven.gangland.lootchest.listener.LootChestListener} handles clicks
 * directly via {@code InventoryClickEvent}/{@code InventoryCloseEvent}/{@code InventoryDragEvent} — take-only,
 * deposits are cancelled there. Unlike the deleted {@code InventoryHandler} this has no per-slot click routing,
 * draggable-slot bookkeeping, or registry integration; implementing {@link InventoryHolder} exists solely so the
 * listener can identify "is this a loot chest's top inventory" by object identity
 * ({@code getInventory().getHolder() == someSession.getInventory()}), never by matching a title string.
 */
public class SharedLootInventory implements InventoryHolder {

	private final int       size;
	private final Inventory inventory;

	public SharedLootInventory(String title, int size) {
		// Bukkit.createInventory requires a multiple of 9 (max 54); chest size ultimately comes from admin-set NBT
		// data (LootChestWand), so normalize defensively rather than let a bad value throw at open time.
		this.size      = Math.min((int) Math.ceil(size / 9.0) * 9, 54);
		this.inventory = Bukkit.createInventory(this, this.size, ChatUtil.color(title));
	}

	public int getSize() {
		return size;
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public void setItem(int slot, ItemStack itemStack) {
		inventory.setItem(slot, itemStack);
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

}
