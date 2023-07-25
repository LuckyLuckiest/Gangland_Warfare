package me.luckyraven.listener.player;

import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PhoneItem implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoinGivePhone(PlayerJoinEvent event) {
		// when the user joins, check if their inventory contains the specific nbt item
		// if they don't have the item then add it to the inventory
		Player player = event.getPlayer();
		if (!hasPhone(player)) givePhoneItem(player);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPhoneInventoryInteract(InventoryClickEvent event) {
		// Prevent the phone item from being thrown or moved in the inventory
		if (event.getClickedInventory() == null) return;

		Inventory clickedInventory = event.getClickedInventory();
		ItemStack clickedItem      = event.getCurrentItem();

		if (clickedItem == null || clickedItem.getType().name().contains("AIR") || clickedItem.getAmount() == 0) return;
		if (clickedInventory.equals(event.getWhoClicked().getInventory())) if (!isPhoneItem(clickedItem)) return;
		if (!SettingAddon.isPhoneMovable()) return;

		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPhoneItemInteract(PlayerInteractEvent event) {
		ItemStack heldItem = event.getItem();

		if (heldItem == null) return;
		if (!isPhoneItem(heldItem)) return;

		event.setCancelled(true);
	}

	@EventHandler
	public void onPhoneItemDrop(PlayerDropItemEvent event) {
		ItemStack droppedItem = event.getItemDrop().getItemStack();

		if (!isPhoneItem(droppedItem)) return;
		if (!SettingAddon.isPhoneDroppable()) return;

		event.setCancelled(true);
	}

	private boolean hasPhone(Player player) {
		Material    phoneItem = getPhoneMaterial();
		ItemStack[] contents  = player.getInventory().getContents();

		for (ItemStack item : contents)
			if (item != null && item.getType() == phoneItem) {
				if (isPhoneItem(item)) return true;
			}

		return false;
	}

	private void givePhoneItem(Player player) {
		Material phoneItem = getPhoneMaterial();

		ItemBuilder itemBuilder = new ItemBuilder(phoneItem).setDisplayName("&3Phone");

		itemBuilder.addTag("uniquePhone", "phone");

		if (!addItem(player, SettingAddon.getPhoneSlot(), itemBuilder.build())) player.sendMessage(
				MessageAddon.INVENTORY_FULL.toString());
	}

	private boolean addItem(Player player, int slot, ItemStack itemStack) {
		if (slot >= player.getInventory().getSize() || slot > 35) return false;

		// TODO it places the item in armor slots
		if (player.getInventory().getItem(slot) != null) return addItem(player, slot + 1, itemStack);
		else player.getInventory().setItem(slot, itemStack);

		return true;
	}

	private boolean isPhoneItem(ItemStack item) {
		ItemBuilder itemCheck = new ItemBuilder(item);
		return itemCheck.hasNBTTag("uniquePhone");
	}

	private Material getPhoneMaterial() {
		Material phoneItem = Material.matchMaterial(SettingAddon.getPhoneItem());
		if (phoneItem == null) return Material.REDSTONE;
		return phoneItem;
	}

}
