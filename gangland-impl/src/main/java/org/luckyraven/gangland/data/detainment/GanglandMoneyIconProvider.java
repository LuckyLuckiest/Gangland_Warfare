package org.luckyraven.gangland.data.detainment;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.MoneyIconProvider;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyItem;

/**
 * Builds display-only money icons by picking a money.yml variation whose {@code [min, max]} range covers the cost
 * (otherwise falls back to the default variation, then to a plain gold ingot).
 */
public final class GanglandMoneyIconProvider implements MoneyIconProvider {

	private final MoneyAddon moneyAddon;

	public GanglandMoneyIconProvider(MoneyAddon moneyAddon) {
		this.moneyAddon = moneyAddon;
	}

	private static ItemStack fallback() {
		ItemStack parsed = XMaterial.GOLD_INGOT.parseItem();
		return parsed != null ? parsed : new ItemStack(Material.GOLD_INGOT);
	}

	@Override
	public ItemStack buildIcon(double cost) {
		MoneyItem variation = pickVariation(cost);
		if (variation == null) return fallback();

		ItemStack stack = new ItemStack(variation.getMaterial());
		return stack.getType() == Material.AIR ? fallback() : stack;
	}

	private MoneyItem pickVariation(double cost) {
		try {
			MoneyItem defaultVariation = moneyAddon.getDefaultVariation();
			if (defaultVariation != null &&
			    cost >= defaultVariation.getMin() && cost <= defaultVariation.getMax()) {
				return defaultVariation;
			}
			return defaultVariation;
		} catch (Exception ignored) {
			return null;
		}
	}
}
