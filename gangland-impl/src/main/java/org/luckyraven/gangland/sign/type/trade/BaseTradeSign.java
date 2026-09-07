package org.luckyraven.gangland.sign.type.trade;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.sign.type.Sign;

import java.util.Arrays;
import java.util.Objects;

public abstract class BaseTradeSign implements Sign {

	protected ItemStack getUniqueOrMaterialItem(String itemName, UniqueItemAddon uniqueItemAddon) {
		// check unique items first
		UniqueItem uniqueItem = uniqueItemAddon.getUniqueItem(itemName);

		if (uniqueItem != null) return uniqueItem.buildItem();

		// fall back to vanilla material
		return Arrays.stream(XMaterial.values())
				.map(XMaterial::get)
				.filter(Objects::nonNull)
				.filter(material -> material.name().equalsIgnoreCase(itemName))
				.findFirst()
				.map(ItemStack::new)
				.orElse(null);
	}
}
