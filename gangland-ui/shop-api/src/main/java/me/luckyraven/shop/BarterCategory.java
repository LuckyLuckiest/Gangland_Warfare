package me.luckyraven.shop;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class BarterCategory {

	private final String          id;
	private final List<ItemStack> items;
	@Setter
	private       String          displayName;
	@Setter
	private       BigDecimal      basePrice;

	public BarterCategory(String id, String displayName, BigDecimal basePrice, List<ItemStack> items) {
		this.id          = id;
		this.displayName = displayName;
		this.basePrice   = basePrice;
		this.items       = items == null ? new ArrayList<>() : new ArrayList<>(items);
	}

	public static BarterCategory empty(String id) {
		return new BarterCategory(id, id, BigDecimal.ZERO, new ArrayList<>());
	}

	public boolean matches(ItemStack stack) {
		return matchingTemplate(stack) != null;
	}

	/**
	 * Returns the stored template {@link ItemStack} that matches {@code stack} by {@link Material}, or {@code null} if
	 * no template in this category matches. The template is where per-item NBT pricing lives, so callers that need the
	 * item-specific value should go through here.
	 */
	public ItemStack matchingTemplate(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		Material type = stack.getType();
		for (ItemStack candidate : items) {
			if (candidate != null && candidate.getType() == type) {
				return candidate;
			}
		}
		return null;
	}

	public void replaceItems(List<ItemStack> replacement) {
		items.clear();
		if (replacement == null) {
			return;
		}
		for (ItemStack stack : replacement) {
			if (stack != null && stack.getType() != Material.AIR) {
				items.add(stack.clone());
			}
		}
	}

}
