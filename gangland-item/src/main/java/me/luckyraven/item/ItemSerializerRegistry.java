package me.luckyraven.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ordered registry of {@link ItemSerializer}s paired with their matching {@link Predicate}. Registration order is
 * priority order — more specific predicates must register first (a unique weapon matches both the unique and the weapon
 * predicate, and we want unique to win). Predicates are provided by the caller, which means all the NBT-key lookups and
 * domain checks live in {@code gangland-impl} and never leak into this module.
 *
 * <p>{@link #serialize(ItemStack)} walks the list, returns {@code <kind.label()>:<extract().toLowerCase()>} for
 * the first predicate that matches and whose serializer yields a non-empty value.
 */
public class ItemSerializerRegistry {

	private final List<Entry> entries = new ArrayList<>();

	public void register(Predicate<ItemStack> predicate, ItemSerializer serializer) {
		entries.add(new Entry(predicate, serializer));
	}

	@Nullable
	public String serialize(ItemStack stack) {
		if (stack == null) {
			return null;
		}
		for (Entry entry : entries) {
			if (!entry.predicate.test(stack)) {
				continue;
			}
			String value = entry.serializer.extract(stack);
			if (value == null || value.isEmpty()) {
				continue;
			}
			return entry.serializer.kind().label() + ":" + value.toLowerCase();
		}
		return null;
	}

	private record Entry(Predicate<ItemStack> predicate, ItemSerializer serializer) {
	}
}
