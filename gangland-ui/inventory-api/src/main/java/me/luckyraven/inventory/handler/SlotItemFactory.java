package me.luckyraven.inventory.handler;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds an {@link ItemBuilder} from the raw slot config values parsed by {@code InventoryAddon}.
 *
 * <p>Extracted here so handler implementations in this module do not need to duplicate the
 * material-validation or tag-assignment logic.
 *
 * <p>An optional {@link #setItemResolver(Function) item resolver} hook can be installed by the host
 * plugin to translate prefixed item strings (e.g. {@code weapon:awp}, {@code wearable:police_vest}, {@code ammo:9mm},
 * {@code unique:phone}) into a fully built {@link ItemStack} via the central {@code ItemParser} living in
 * {@code gangland-item}. When the resolver returns a non-null ItemStack, the slot's {@code Name}/{@code Lore} only
 * override the parser-supplied values when the YAML explicitly sets them — otherwise the parsed item's own name and
 * lore are kept.
 */
public final class SlotItemFactory {

	private static @Nullable Function<String, ItemStack> itemResolver;

	private SlotItemFactory() { }

	/**
	 * Installs the prefixed-string resolver. Pass {@code null} to disable. Typically wired once at startup from the
	 * host plugin (e.g. {@code SlotItemFactory.setItemResolver(itemParser::parse)}).
	 */
	public static void setItemResolver(@Nullable Function<String, ItemStack> resolver) {
		itemResolver = resolver;
	}

	public static ItemBuilder create(String item, @Nullable String itemName, Map<String, Object> data,
	                                 List<String> lore, boolean enchanted) {
		ItemStack resolved = null;
		if (itemResolver != null && item != null && item.contains(":")) {
			resolved = itemResolver.apply(item);
		}

		ItemBuilder itemBuilder;
		if (resolved != null) {
			itemBuilder = new ItemBuilder(resolved);
		} else {
			itemBuilder = new ItemBuilder(validateMaterial(item).get());
		}

		String  color           = (String) data.get("color");
		String  dataInfo        = (String) data.get("data");
		Integer customModelData = (Integer) data.get("customModelData");

		if (color != null) itemBuilder.addTag("color", color);
		if (dataInfo != null) itemBuilder.addTag("data", dataInfo);

		if (resolved != null) {
			// Only override the parser-supplied name/lore when the slot YAML explicitly set them.
			if (itemName != null) itemBuilder.setDisplayName(itemName);
			if (!lore.isEmpty()) itemBuilder.setLore(lore);
		} else {
			itemBuilder.setDisplayName(itemName);
			itemBuilder.setLore(lore);
		}

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
