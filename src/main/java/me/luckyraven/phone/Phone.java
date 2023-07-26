package me.luckyraven.phone;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Phone {

	@Getter
	private final String      name;
	private final Inventory   inventory;
	private final ItemBuilder item;

	@Getter
	private String displayName;

	public Phone(String name) {
		this.name = name;
		this.displayName = name;
		this.inventory = new Inventory(JavaPlugin.getPlugin(Gangland.class), displayName, Inventory.MAX_SLOTS);
		this.item = new ItemBuilder(getPhoneMaterial());

		populateInventory();
	}

	public void setDisplayName(String displayName) {
		inventory.rename(displayName);
		this.displayName = displayName;
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

	public Material getPhoneMaterial() {
		Material phoneItem = Material.matchMaterial(SettingAddon.getPhoneItem());
		if (phoneItem == null) return Material.REDSTONE;
		return phoneItem;
	}

	private void populateInventory() {
		// TODO
	}

	public void addPhoneToInventory() {
		// TODO
	}

}
