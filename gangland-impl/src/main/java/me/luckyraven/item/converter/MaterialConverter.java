package me.luckyraven.item.converter;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.item.ItemAttributes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public class MaterialConverter extends ItemAttributes {

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		try {
			String              lookup    = (modifier != null && !modifier.isBlank()) ? modifier : type;
			Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(lookup.toUpperCase());

			Material material;
			if (xMaterial.isPresent()) {
				material = xMaterial.get().get();
			} else {
				material = Material.valueOf(lookup.toUpperCase());
			}

			if (material == null) return null;

			ItemStack itemStack = new ItemStack(material);

			applyAttributes(itemStack, attributes);

			return itemStack;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

}
