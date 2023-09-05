package me.luckyraven.util;

import com.cryptomorin.xseries.XMaterial;
import com.google.common.base.Preconditions;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class InventoryUtil {

	public static void aroundSlot(InventoryHandler inventoryHandler, int slot, Material material) {
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(null).build();

		// left-mid : 21 -> 21 - 9 = 12, 21 + 9
		// mid      : 22 -> 22 - 9 = 13, 22 + 9
		// right-mid: 23 -> 23 - 9 = 14, 23 + 9

		int topLeft = slot - 10;

		for (int i = topLeft; i < topLeft + 3; i++)
			for (int j = i; j < i + 9 * 2 + 1; j += 9)
				try {
					if (inventoryHandler.getInventory().getItem(j) != null || j == slot) continue;

					inventoryHandler.setItem(j, item, false);
				} catch (ArrayIndexOutOfBoundsException ignored) {
				}
	}

	public static void fillInventory(InventoryHandler inventoryHandler) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		for (int i = 0; i < inventoryHandler.getSize(); i++) {
			if (inventoryHandler.getInventory().getItem(i) != null) continue;

			inventoryHandler.getInventory().setItem(i, item);
		}
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, int row, ItemStack... items) {
		int rows = inventoryHandler.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));

		// always nine slots
		int slot = (row - 1) * 9;
		for (int i = 0; i < 9; i++) {

			if (inventoryHandler.getInventory().getItem(slot + i) != null) continue;

			if (i < items.length) inventoryHandler.getInventory().setItem(slot + i, items[i]);
			else inventoryHandler.getInventory().setItem(slot + i, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, int row, Material material, String name,
	                                  boolean all) {
		int rows = inventoryHandler.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
		                            String.format("Rows need to be between 1 and %d inclusive", rows));
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[9];

			Arrays.fill(items, item);
		}

		horizontalLine(inventoryHandler, row, items);
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, int row) {
		horizontalLine(inventoryHandler, row, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public static void verticalLine(InventoryHandler inventoryHandler, int column, ItemStack... items) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		// from 1-6
		int rows = inventoryHandler.getSize() / 9;
		for (int i = 0; i < rows; i++) {
			int slot = (column - 1) + 9 * i;

			if (inventoryHandler.getInventory().getItem(slot) != null) continue;

			if (i < items.length) inventoryHandler.getInventory().setItem(slot, items[i]);
			else inventoryHandler.getInventory().setItem(slot, new ItemBuilder(getLineItem()).setDisplayName(
					SettingAddon.getInventoryLineName()).build());
		}
	}

	public static void verticalLine(InventoryHandler inventoryHandler, int column, Material material, String name,
	                                boolean all) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(name).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[inventoryHandler.getSize() / 9];

			Arrays.fill(items, item);
		}

		verticalLine(inventoryHandler, column, items);
	}

	public static void verticalLine(InventoryHandler inventoryHandler, int column) {
		verticalLine(inventoryHandler, column, getLineItem(), SettingAddon.getInventoryLineName(), true);
	}

	public static void createBoarder(InventoryHandler inventoryHandler) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem());

		ItemStack item = itemBuilder.setDisplayName(SettingAddon.getInventoryFillName()).build();

		int rows = inventoryHandler.getSize() / 9;

		for (int i = 0; i < inventoryHandler.getSize(); i++) {
			int row = i / 9;

			// place items on the borders only
			if (row == 0 || row == rows - 1 || i % 9 == 0 || i % 9 == 8) {
				if (inventoryHandler.getInventory().getItem(i) != null) continue;
				inventoryHandler.getInventory().setItem(i, item);
			}
		}
	}

	public static Material getFillItem() {
		Material item = XMaterial.valueOf(SettingAddon.getInventoryFillItem()).parseMaterial();
		return item != null ? item : XMaterial.BLACK_STAINED_GLASS_PANE.parseMaterial();
	}

	public static Material getLineItem() {
		Material item = XMaterial.valueOf(SettingAddon.getInventoryLineItem()).parseMaterial();
		return item != null ? item : XMaterial.WHITE_STAINED_GLASS_PANE.parseMaterial();
	}

}
