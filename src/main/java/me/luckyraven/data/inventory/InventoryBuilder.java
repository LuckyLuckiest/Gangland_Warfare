package me.luckyraven.data.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.part.Slot;
import me.luckyraven.data.user.User;
import me.luckyraven.util.InventoryUtil;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.List;

public record InventoryBuilder(InventoryData inventoryData, String permission) {

	public InventoryHandler createInventory(Gangland gangland, User<Player> user, String name) {
		InventoryHandler handler = user.getInventory(name);

		// create a new instance
		if (handler == null) {
			Player player = user.getUser();

			String     displayName = inventoryData.getDisplayName();
			int        size        = inventoryData.getSize();
			List<Slot> slots       = inventoryData.getSlots();

			// it is special when there is a click event
			handler = new InventoryHandler(gangland, gangland.usePlaceholder(player, displayName), size, user, false);

			for (Slot slot : slots) {
				int         usedSlot = slot.getSlot();
				ItemBuilder item     = slot.getItem();
				if (item == null) continue;

				// handles color tag
				String   colorTag = "color";
				Material type     = item.getType();
				if (item.hasNBTTag(colorTag)) {
					// special treatment for colored data
					String value = gangland.usePlaceholder(player, item.getTagData(colorTag).toString());

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
					String value = gangland.usePlaceholder(player, item.getTagData(headTag).toString());
					item.modifyNBT(nbt -> nbt.setString("SkullOwner", value));
				}

				ItemBuilder newItem = new ItemBuilder(type);

				String itemDisplayName = gangland.usePlaceholder(player, item.getDisplayName());
				newItem.setDisplayName(itemDisplayName);

				List<String> lore = item.getLore().stream().map(s -> gangland.usePlaceholder(player, s)).toList();
				newItem.setLore(lore);

				if (!item.getEnchantments().isEmpty())
					newItem.addEnchantment(Enchantment.DURABILITY, 1).addItemFlags(ItemFlag.HIDE_ENCHANTS);

				handler.setItem(usedSlot, newItem, slot.isDraggable(), slot.getClickableSlot());
			}

			List<Integer> verticalLine   = inventoryData.getVerticalLine();
			List<Integer> horizontalLine = inventoryData.getHorizontalLine();

			if (!verticalLine.isEmpty()) for (int line : verticalLine)
				InventoryUtil.verticalLine(handler, line);

			if (!horizontalLine.isEmpty()) for (int line : horizontalLine)
				InventoryUtil.horizontalLine(handler, line);

			if (inventoryData.isBorder()) InventoryUtil.createBoarder(handler);
			else if (inventoryData.isFill()) InventoryUtil.fillInventory(handler);
		}

		return handler;
	}

}
