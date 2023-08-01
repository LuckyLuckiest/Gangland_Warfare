package me.luckyraven.phone;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Phone {


	private final @Getter String      name;
	private final         Inventory   inventory;
	private final         ItemBuilder item;


	private @Getter String displayName;

	public Phone(String name) {
		this.name = name;
		this.displayName = name;
		this.inventory = new Inventory(JavaPlugin.getPlugin(Gangland.class), displayName, Inventory.MAX_SLOTS);
		this.item = new ItemBuilder(getPhoneMaterial()).setDisplayName(displayName).addTag("uniqueItem", "phone");

		populateInventory();
	}

	public static Material getPhoneMaterial() {
		Material phoneItem = Material.matchMaterial(SettingAddon.getPhoneItem());
		if (phoneItem == null) return Material.REDSTONE;
		return phoneItem;
	}

	public static boolean isPhone(ItemStack item) {
		return new ItemBuilder(item).hasNBTTag("uniqueItem");
	}

	public static boolean hasPhone(Player player) {
		Material    phoneItem = getPhoneMaterial();
		ItemStack[] contents  = player.getInventory().getContents();

		for (ItemStack item : contents)
			if (item != null && item.getType() == phoneItem) if (isPhone(item)) return true;

		return false;
	}

	public void setDisplayName(String displayName) {
		inventory.rename(displayName);
		this.displayName = displayName;
		this.item.setDisplayName(displayName);
	}

	public void openInventory(Player player) {
		inventory.open(player);
	}

	public void closeInventory(Player player) {
		inventory.close(player);
	}

	public org.bukkit.inventory.Inventory getInventory() {
		return inventory.getInventory();
	}

	private void populateInventory() {
		// TODO
	}

	public void addPhoneToInventory(Player player) {
		if (!addItem(player, SettingAddon.getPhoneSlot())) player.sendMessage(MessageAddon.INVENTORY_FULL.toString());
	}

	private boolean addItem(Player player, int slot) {
		if (slot >= player.getInventory().getSize() || slot > 35) return false;

		if (player.getInventory().getItem(slot) != null) return addItem(player, slot + 1);
		else player.getInventory().setItem(slot, item.build());

		return true;
	}

}
