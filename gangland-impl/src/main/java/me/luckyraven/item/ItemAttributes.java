package me.luckyraven.item;

import me.luckyraven.core.color.Color;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Arrays;
import java.util.Map;

public abstract class ItemAttributes implements ItemConverter {

	public void applyAttributes(ItemStack itemStack, Map<String, String> attributes) {
		if (attributes.isEmpty()) return;

		ItemMeta meta = itemStack.getItemMeta();

		if (meta == null) return;

		if (attributes.containsKey("name")) {
			String name = GanglandChatUtil.color(attributes.get("name"));

			meta.setDisplayName(name);
		}

		if (attributes.containsKey("lore")) {
			var lore      = attributes.get("lore");
			var loreLines = Arrays.stream(lore.split(",")).map(String::trim).map(GanglandChatUtil::color).toList();

			meta.setLore(loreLines);
		}

		if (attributes.containsKey("color") && meta instanceof LeatherArmorMeta leatherArmorMeta) {
			try {
				String color        = attributes.get("color");
				var    leatherColor = Color.valueOf(color.toUpperCase()).getBukkitColor();

				leatherArmorMeta.setColor(leatherColor);
			} catch (IllegalArgumentException ignored) { }
		}

		itemStack.setItemMeta(meta);
	}

}
