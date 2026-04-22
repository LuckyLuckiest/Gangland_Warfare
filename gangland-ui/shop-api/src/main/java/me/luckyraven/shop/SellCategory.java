package me.luckyraven.shop;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.item.ItemSerializerRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public final class SellCategory {

	private final String          id;
	private final List<ItemStack> items;
	@Setter
	private       String          displayName;
	@Setter
	private       BigDecimal      basePrice;

	public SellCategory(String id, String displayName, BigDecimal basePrice, List<ItemStack> items) {
		this.id          = id;
		this.displayName = displayName;
		this.basePrice   = basePrice;
		this.items       = items == null ? new ArrayList<>() : new ArrayList<>(items);
	}

	public static SellCategory empty(String id) {
		return new SellCategory(id, id, BigDecimal.ZERO, new ArrayList<>());
	}

	public boolean matches(ItemStack stack, ItemSerializerRegistry registry) {
		return matchingTemplate(stack, registry) != null;
	}

	/**
	 * Returns the stored template {@link ItemStack} that matches {@code stack} by canonical identity (via
	 * {@link ItemSerializerRegistry#serialize}, e.g. {@code "CAR:pickup_truck"}), or {@code null} if no template
	 * matches. Using the serializer key distinguishes items that share a {@link Material} but differ by NBT identity
	 * (different car models, weapon variants, custom-model-data distinctions, etc.). The template is where per-item
	 * NBT metadata (e.g. the {@code sell_price} tag) lives, so callers that need item-specific pricing go through here.
	 */
	public ItemStack matchingTemplate(ItemStack stack, ItemSerializerRegistry registry) {
		if (stack == null || registry == null) {
			return null;
		}
		String stackKey = registry.serialize(stack);
		if (stackKey == null) {
			return null;
		}
		for (ItemStack candidate : items) {
			if (candidate == null) {
				continue;
			}
			if (Objects.equals(stackKey, registry.serialize(candidate))) {
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
