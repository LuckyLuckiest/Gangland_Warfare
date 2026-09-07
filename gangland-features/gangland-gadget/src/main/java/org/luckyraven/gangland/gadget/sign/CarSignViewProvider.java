package org.luckyraven.gangland.gadget.sign;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;
import org.luckyraven.gangland.sign.extension.SignViewProvider;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code view} sign's car branch, moved out of the core's {@code ViewInventoryAspect.openCarView} verbatim
 * when the sign extension seam was introduced.
 */
public class CarSignViewProvider implements SignViewProvider {

	private final JavaPlugin  plugin;
	private final CarManager  carManager;

	public CarSignViewProvider(JavaPlugin plugin, CarManager carManager) {
		this.plugin     = plugin;
		this.carManager = carManager;
	}

	@Override
	public boolean open(Player player, String content) {
		Car car = carManager.getCar(content);
		if (car == null) {
			return false;
		}

		openCarView(player, car);
		return true;
	}

	private void openCarView(Player player, Car car) {
		String title = "&6View: &e" + car.getDisplayName();

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		List<String> lore = new ArrayList<>();
		lore.add("&7Speed: &f" + car.getMaxSpeed() + " &7blocks/tick");
		lore.add("&7Acceleration: &f" + car.getAcceleration());
		lore.add("&7Health: &f" + car.getMaxHealth() + " HP");
		lore.add("&7Durability: &f" + car.getMaxDurability());

		if (car.isFuelEnabled()) {
			lore.add("&7Fuel: &fRequired");
			lore.add("&7Fuel Type: &f" + car.getFuelKey());
		} else {
			lore.add("&7Fuel: &aUnlimited");
		}

		ItemStack carItem = new ItemBuilder(car.buildItem(player)).setLore(lore).build();

		inventory.setItem(4, carItem, false, null);

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}
}
