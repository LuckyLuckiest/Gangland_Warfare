package me.luckyraven.inventory;

import com.cryptomorin.xseries.XEnchantment;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Placeholder;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public record InventoryBuilder(InventoryData inventoryData, String permission) {

	public InventoryHandler createInventory(JavaPlugin plugin, Placeholder placeholder, Player player, Fill fill,
											Fill line) {
		// create a new instance
		String     displayName = inventoryData.getDisplayName();
		int        size        = inventoryData.getSize();
		List<Slot> slots       = inventoryData.getSlots();

		// it is special when there is a click event
		String           title   = placeholder.convert(player, displayName);
		InventoryHandler handler = new InventoryHandler(plugin, title, size, player);

		for (Slot slot : slots) {
			int         usedSlot = slot.getSlot();
			ItemBuilder item     = slot.getItem();
			if (item == null) continue;

			// handles color tag
			String   colorTag = "color";
			Material type     = item.getType();
			if (item.hasNBTTag(colorTag)) {
				// special treatment for colored data
				String value = placeholder.convert(player, item.getStringTagData(colorTag));

				MaterialType material = MaterialType.WOOL;
				for (MaterialType materialType : MaterialType.values()) {
					if (!type.name().contains(materialType.name())) continue;

					material = materialType;
					break;
				}

				type = ColorUtil.getMaterialByColor(value, material.name());
			}

			// handles head data
			String headTag = "head";
			if (item.hasNBTTag(headTag)) {
				String value = placeholder.convert(player, item.getStringTagData(headTag));
				item.modifyNBT(nbt -> nbt.setString("SkullOwner", value));
			}

			ItemBuilder newItem = new ItemBuilder(type);

			String itemDisplayName = placeholder.convert(player, item.getDisplayName());
			newItem.setDisplayName(itemDisplayName);

			List<String> lore = item.getLore()
					.stream().map(s -> placeholder.convert(player, s)).toList();
			newItem.setLore(lore);

			if (!item.getEnchantments().isEmpty()) newItem.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
														  .addItemFlags(ItemFlag.HIDE_ENCHANTS,
																		ItemFlag.HIDE_ATTRIBUTES);

			handler.setItem(usedSlot, newItem, slot.isDraggable(), slot.getClickableSlot());
		}

		List<Integer> verticalLine   = inventoryData.getVerticalLine();
		List<Integer> horizontalLine = inventoryData.getHorizontalLine();

		if (!verticalLine.isEmpty()) {
			for (int l : verticalLine)
				InventoryUtil.verticalLine(handler, line, l);
		}

		if (!horizontalLine.isEmpty()) {
			for (int l : horizontalLine)
				InventoryUtil.horizontalLine(handler, line, l);
		}

		if (inventoryData.isBorder()) {
			InventoryUtil.createBoarder(handler, fill);
		} else if (inventoryData.isFill()) {
			InventoryUtil.fillInventory(handler, fill);
		}

		return handler;
	}

}
