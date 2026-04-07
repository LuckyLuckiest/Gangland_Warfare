package me.luckyraven.inventory.handler;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.inventory.ItemFlag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link ItemBuilder} from the raw slot config values parsed by {@code InventoryAddon}.
 *
 * <p>Extracted here so handler implementations in this module do not need to duplicate the
 * material-validation or tag-assignment logic.
 */
public final class SlotItemFactory {

	private SlotItemFactory() { }

	public static ItemBuilder create(String item, String itemName, Map<String, Object> data,
									 List<String> lore, boolean enchanted) {
		ItemBuilder itemBuilder = new ItemBuilder(validateMaterial(item).get());

		String  color           = (String) data.get("color");
		String  dataInfo        = (String) data.get("data");
		Integer customModelData = (Integer) data.get("customModelData");

		if (color != null) itemBuilder.addTag("color", color);
		if (dataInfo != null) itemBuilder.addTag("data", dataInfo);

		itemBuilder.setDisplayName(itemName);
		itemBuilder.setLore(lore);

		if (customModelData != null && customModelData > 0) {
			itemBuilder.setCustomModelData(customModelData);
		}

		if (enchanted) {
			itemBuilder.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
					   .addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}

		return itemBuilder;
	}

	private static XMaterial validateMaterial(String value) {
		MaterialType[] types = MaterialType.values();
		if (Arrays.stream(types).anyMatch(t -> t.name().contains(value))) {
			return XMaterial.matchXMaterial("BLACK_" + value).orElse(XMaterial.BLACK_WOOL);
		}
		return XMaterial.valueOf(value);
	}

}
