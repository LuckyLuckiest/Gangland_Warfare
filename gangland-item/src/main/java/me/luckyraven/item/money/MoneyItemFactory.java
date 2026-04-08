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

	private static final String AMOUNT_PLACEHOLDER = "%amount%";

	private MoneyItemFactory() {
	}

	/**
	 * Builds a money {@link ItemStack} of size 1 from the given variation and rolled amount.
	 *
	 * @param variation the variation definition
	 * @param amount the rolled amount to embed in NBT and substitute into lore
	 * @param depositService used to resolve {@code %gangland_*%} placeholders in the display name and lore via the
	 * 		impl-side placeholder pipeline
	 *
	 * @return a fresh {@code ItemStack} carrying the cash NBT tags
	 */
	public static ItemStack build(MoneyItem variation, int amount, MoneyDepositService depositService) {
		return build(variation, amount, depositService, 1);
	}

	/**
	 * Builds a money {@link ItemStack} with the given stack count. Two money items with the same variation id and the
	 * same rolled amount have identical NBT and will stack naturally; that lets the give command hand out a single
	 * stack of N money items instead of N separate stacks-of-one when the underlying material allows it.
	 *
	 * @param variation the variation definition
	 * @param amount the rolled amount to embed in NBT and substitute into lore
	 * @param depositService used to resolve {@code %gangland_*%} placeholders in the display name and lore via the
	 * 		impl-side placeholder pipeline
	 * @param stackCount how many money items the resulting stack should contain (caller is responsible for clamping
	 * 		to the material's max stack size)
	 *
	 * @return a fresh {@code ItemStack} carrying the cash NBT tags
	 */
	public static ItemStack build(MoneyItem variation, int amount, MoneyDepositService depositService, int stackCount) {
		List<String> renderedLore = new ArrayList<>(variation.getLore().size());
		for (String line : variation.getLore()) {
			renderedLore.add(applyPlaceholders(line, depositService, amount));
		}

		String renderedName = applyPlaceholders(variation.getDisplayName(), depositService, amount);

		ItemBuilder builder = new ItemBuilder(variation.getMaterial())
				.setAmount(Math.max(1, stackCount))
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

	private static String applyPlaceholders(String input, MoneyDepositService depositService, int amount) {
		if (input == null) return "";
		String replaced = input.replace(AMOUNT_PLACEHOLDER, Integer.toString(amount));
		return depositService.resolvePlaceholders(null, replaced);
	}

}
