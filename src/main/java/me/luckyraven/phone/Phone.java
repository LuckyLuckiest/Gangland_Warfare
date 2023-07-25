package me.luckyraven.phone;

import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.gui.InventoryGUI;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class Phone {

	@Getter
	private final String       name;
	private final InventoryGUI inventory;
	private final ItemBuilder  item;

	@Getter
	private String displayName;

	public Phone(String name) {
		this.name = name;
		this.displayName = name;
		this.inventory = new InventoryGUI(displayName, InventoryGUI.MAX_SLOTS);
		this.item = new ItemBuilder(getPhoneMaterial());

		populateInventory();
	}

	public void setDisplayName(String displayName) {
		inventory.rename(displayName);
		this.displayName = displayName;
	}

	public void openInventory(User<Player> user) {
		inventory.open(user);
	}

	public void closeInventory(User<Player> user) {
		inventory.close(user);
	}

	public Inventory getInventory() {
		return inventory.getInventory();
	}

	public Material getPhoneMaterial() {
		Material phoneItem = Material.matchMaterial(SettingAddon.getPhoneItem());
		if (phoneItem == null) return Material.REDSTONE;
		return phoneItem;
	}

	private void populateInventory() {

	}

}
