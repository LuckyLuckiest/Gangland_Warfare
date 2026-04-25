package me.luckyraven.item.money;

import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemConverter;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Item converter that lets {@code ItemParser} produce cash items via prefixed strings such as {@code money:large} or
 * {@code money:large{amount=500}}.
 *
 * <p>If no modifier is given, the configured default variation is used. If an explicit
 * {@code amount=N} attribute is supplied, it overrides the variation's random roll (clamped into the variation's [min,
 * max] range).
 */
@RequiredArgsConstructor
public class MoneyConverter implements ItemConverter {

	private static final String AMOUNT_ATTRIBUTE = "amount";

	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService depositService;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		MoneyItem variation = (modifier == null || modifier.isBlank())
		                      ? moneyAddon.getDefaultVariation()
		                      : moneyAddon.getVariation(modifier);

		if (variation == null) return null;

		int amount = moneyAddon.rollAmount(variation);
		if (attributes != null && attributes.containsKey(AMOUNT_ATTRIBUTE)) {
			try {
				int requested = Integer.parseInt(attributes.get(AMOUNT_ATTRIBUTE));
				amount = Math.clamp(requested, variation.getMin(), variation.getMax());
			} catch (NumberFormatException ignored) {
				// fall back to the rolled amount
			}
		}

		return MoneyItemFactory.build(variation, amount, depositService);
	}

}
