package me.luckyraven.bukkit.inventory;

import com.cryptomorin.xseries.XMaterial;
import com.google.common.base.Preconditions;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class InventoryAddons {

	public static void aroundSlot(Inventory inventory, int slot, Material material) {
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(null).build();

		// left-mid : 21 -> 21 - 9 = 12, 21 + 9
		// mid      : 22 -> 22 - 9 = 13, 22 + 9
		// right-mid: 23 -> 23 - 9 = 14, 23 + 9

		int topLeft = slot - 10;

		for (int i = topLeft; i < topLeft + 3; i++)
			for (int j = i; j < i + 9 * 2 + 1; j += 9)
				try {
					if (inventory.getInventory().getItem(j) != null || j == slot) continue;

					inventory.setItem(j, item, false);
				} catch (ArrayIndexOutOfBoundsException ignored) {
				}
	}

	public static void fillInventory(Inventory inventory) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getInventory().getItem(i) != null) continue;

			inventory.getInventory().setItem(i, item);
		}
	}

	public static void horizontalLine(Inventory inventory, int row, ItemStack... items) {
		int rows = inventory.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));

		// always 9 slots
		int slot = (row - 1) * 9;
		for (int i = 0; i < 9; i++) {

			if (inventory.getInventory().getItem(slot + i) != null) continue;

			if (i < items.length) inventory.getInventory().setItem(slot + i, items[i]);
			else inventory.getInventory().setItem(slot + i, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public static void horizontalLine(Inventory inventory, int row, Material material, String name, boolean all) {
		int rows = inventory.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[9];

			Arrays.fill(items, item);
		}

		horizontalLine(inventory, row, items);
	}

	public static void horizontalLine(Inventory inventory, int row) {
		horizontalLine(inventory, row, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public static void verticalLine(Inventory inventory, int column, ItemStack... items) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		// from 1-6
		int rows = inventory.getSize() / 9;
		for (int i = 0; i < rows; i++) {
			int slot = (column - 1) + 9 * i;

			if (inventory.getInventory().getItem(slot) != null) continue;

			if (i < items.length) inventory.getInventory().setItem(slot, items[i]);
			else inventory.getInventory().setItem(slot, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public static void verticalLine(Inventory inventory, int column, Material material, String name, boolean all) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[inventory.getSize() / 9];

			Arrays.fill(items, item);
		}

		verticalLine(inventory, column, items);
	}

	public static void verticalLine(Inventory inventory, int column) {
		verticalLine(inventory, column, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public static void createBoarder(Inventory inventory) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		int rows = inventory.getSize() / 9;

		for (int i = 0; i < inventory.getSize(); i++) {
			int row = i / 9;

			// place items on the borders only
			if (row == 0 || row == rows - 1 || i % 9 == 0 || i % 9 == 8) {
				if (inventory.getInventory().getItem(i) != null) continue;
				inventory.getInventory().setItem(i, item);
			}
		}
	}

	private static Material getFillItem() {
		Material item = XMaterial.matchXMaterial(SettingAddon.getInventoryFillItem())
		                         .stream()
		                         .toList()
		                         .get(0)
		                         .parseMaterial();
		return item != null ? item : XMaterial.BLACK_STAINED_GLASS_PANE.parseMaterial();
	}

	private static Material getLineItem() {
		Material item = XMaterial.matchXMaterial(SettingAddon.getInventoryLineItem())
		                         .stream()
		                         .toList()
		                         .get(0)
		                         .parseMaterial();
		return item != null ? item : XMaterial.WHITE_STAINED_GLASS_PANE.parseMaterial();
	}

}
