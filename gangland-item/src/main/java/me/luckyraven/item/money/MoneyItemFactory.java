package me.luckyraven.item.money;

import me.luckyraven.util.ItemBuilder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link ItemStack} representation of a {@link MoneyItem} variation, applying NBT tags and lore
 * placeholders.
 *
 * <p>The factory is stateless and instance-free; everything is exposed as static helpers so the converter,
 * the drop listener, and the right-click listener can all build/inspect items uniformly.
 */
public final class MoneyItemFactory {

	private static final String SYMBOL_PLACEHOLDER = "%symbol%";
	private static final String AMOUNT_PLACEHOLDER = "%amount%";

	private MoneyItemFactory() {
	}

	/**
	 * Builds a money {@link ItemStack} from the given variation and rolled amount.
	 *
	 * @param variation the variation definition
	 * @param amount the rolled amount to embed in NBT and substitute into lore
	 * @param currencySymbol the currency symbol used for {@code %symbol%} placeholders in display name and lore
	 *
	 * @return a fresh {@code ItemStack} carrying the cash NBT tags
	 */
	public static ItemStack build(MoneyItem variation, int amount, String currencySymbol) {
		List<String> renderedLore = new ArrayList<>(variation.getLore().size());
		for (String line : variation.getLore()) {
			renderedLore.add(applyPlaceholders(line, currencySymbol, amount));
		}

		String renderedName = applyPlaceholders(variation.getDisplayName(), currencySymbol, amount);

		ItemBuilder builder = new ItemBuilder(variation.getMaterial())
				.setAmount(1)
				.setDisplayName(renderedName)
				.setLore(renderedLore)
				.addTag(MoneyItemUtil.MARKER_TAG, (byte) 1)
				.addTag(MoneyItemUtil.VARIATION_TAG, variation.getId())
				.addTag(MoneyItemUtil.AMOUNT_TAG, amount);

		if (variation.getCustomModelData() > 0) {
			builder.setCustomModelData(variation.getCustomModelData());
		}

		if (variation.isGlow()) {
			builder.addEnchantment(Enchantment.UNBREAKING, 1);
			builder.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}

		return builder.build();
	}

	private static String applyPlaceholders(String input, String currencySymbol, int amount) {
		if (input == null) return "";
		return input.replace(SYMBOL_PLACEHOLDER, currencySymbol)
		            .replace(AMOUNT_PLACEHOLDER, Integer.toString(amount));
	}

}
