package me.luckyraven.inventory.util;

import com.cryptomorin.xseries.XMaterial;
import com.google.common.base.Preconditions;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public final class InventoryUtil {

	private InventoryUtil() { }

	public static String titleRefactor(@NotNull String title) {
		Preconditions.checkNotNull(title, "Title can't be null");

		String pattern = "[^a-z0-9/._-]";
		return ChatUtil.replaceColorCodes(title, "").replaceAll(" ", "_").toLowerCase().replaceAll(pattern, "");
	}

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
				} catch (ArrayIndexOutOfBoundsException ignored) { }
	}

	public static void fillInventory(InventoryHandler inventoryHandler, Fill fill) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem(fill.material()));

		ItemStack item = itemBuilder.setDisplayName(fill.name()).build();

		for (int i = 0; i < inventoryHandler.getSize(); i++) {
			if (inventoryHandler.getInventory().getItem(i) != null) continue;

			inventoryHandler.getInventory().setItem(i, item);
		}
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, Fill line, int row, ItemStack... items) {
		int rows = inventoryHandler.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
									String.format("Rows need to be between 1 and %d inclusive", rows));

		// always nine slots
		int slot = (row - 1) * 9;
		for (int i = 0; i < 9; i++) {

			if (inventoryHandler.getInventory().getItem(slot + i) != null) continue;

			if (i < items.length) {
				inventoryHandler.getInventory().setItem(slot + i, items[i]);
			} else {
				ItemBuilder itemBuilder = new ItemBuilder(getLineItem(line.material())).setDisplayName(line.name());
				inventoryHandler.getInventory().setItem(slot + i, itemBuilder.build());
			}
		}
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, Fill line, int row, Material material,
									  boolean all) {
		int rows = inventoryHandler.getSize() / 9;
		Preconditions.checkArgument(row > 0 && row < rows + 1,
									String.format("Rows need to be between 1 and %d inclusive", rows));
		ItemBuilder itemBuilder = new ItemBuilder(material);
		ItemStack   item        = itemBuilder.setDisplayName(line.name()).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[9];

			Arrays.fill(items, item);
		}

		horizontalLine(inventoryHandler, line, row, items);
	}

	public static void horizontalLine(InventoryHandler inventoryHandler, Fill line, int row) {
		horizontalLine(inventoryHandler, line, row, getLineItem(line.material()), true);
	}

	public static void verticalLine(InventoryHandler inventoryHandler, Fill line, int column, ItemStack... items) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		// from 1-6
		int rows = inventoryHandler.getSize() / 9;
		for (int i = 0; i < rows; i++) {
			int slot = (column - 1) + 9 * i;

			if (inventoryHandler.getInventory().getItem(slot) != null) continue;

			if (i < items.length) inventoryHandler.getInventory().setItem(slot, items[i]);
			else {
				ItemBuilder itemBuilder = new ItemBuilder(getLineItem(line.material())).setDisplayName(line.name());
				inventoryHandler.getInventory().setItem(slot, itemBuilder.build());
			}
		}
	}

	public static void verticalLine(InventoryHandler inventoryHandler, Fill line, int column, boolean all) {
		Preconditions.checkArgument(column > 0 && column < 9, "Columns need to be between 1 and 9 inclusive");

		ItemBuilder itemBuilder = new ItemBuilder(getLineItem(line.material()));
		ItemStack   item        = itemBuilder.setDisplayName(line.name()).build();

		ItemStack[] items = {item};
		if (all) {
			items = new ItemStack[inventoryHandler.getSize() / 9];

			Arrays.fill(items, item);
		}

		verticalLine(inventoryHandler, line, column, items);
	}

	public static void verticalLine(InventoryHandler inventoryHandler, Fill line, int column) {
		verticalLine(inventoryHandler, line, column, true);
	}

	public static void createBoarder(InventoryHandler inventoryHandler, Fill fill) {
		ItemBuilder itemBuilder = new ItemBuilder(getFillItem(fill.material()));

		ItemStack item = itemBuilder.setDisplayName(fill.name()).build();

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

	public static Material getFillItem(String inventoryFillItem) {
		Material            material          = null;
		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(inventoryFillItem);

		if (xMaterialOptional.isPresent()) material = xMaterialOptional.get().get();

		return material != null ? material : XMaterial.BLACK_STAINED_GLASS_PANE.get();
	}

	public static Material getLineItem(String inventoryLineItem) {
		Material            material          = null;
		Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(inventoryLineItem);

		if (xMaterialOptional.isPresent()) material = xMaterialOptional.get().get();

		return material != null ? material : XMaterial.WHITE_STAINED_GLASS_PANE.get();
	}

}
