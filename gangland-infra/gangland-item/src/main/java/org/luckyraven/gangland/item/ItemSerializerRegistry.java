package org.luckyraven.gangland.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ordered registry of {@link ItemSerializer}s paired with their matching {@link Predicate}. Registration order is
 * priority order — more specific predicates must register first (a unique weapon matches both the unique and the weapon
 * predicate, and we want unique to win). Predicates are provided by the caller, which means all the NBT-key lookups and
 * domain checks live in {@code gangland-impl} and never leak into this module.
 *
 * <p>Entries are kept sorted by priority (highest first) using a <em>stable</em> sort, so within a priority tier
 * registration order is preserved exactly. A runtime module always registers its serializers after the core's own
 * CONFIG-phase beans have run, so the core's catch-all ({@code MATERIAL}) opts out of plain insertion order by
 * registering itself at {@link #CATCH_ALL_PRIORITY} — the lowest possible priority — so a later (module)
 * registration at the default priority always sorts ahead of it and gets a chance to match first.
 *
 * <p>{@link #serialize(ItemStack)} walks the list, returns {@code <kind.label()>:<extract().toLowerCase()>} for
 * the first predicate that matches and whose serializer yields a non-empty value.
 */
public class ItemSerializerRegistry {

	/**
	 * Priority for a catch-all serializer that must always sort after every other registration, no matter how many
	 * modules register afterwards at the default priority.
	 */
	public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;

	private final List<Entry> entries = new ArrayList<>();

	public void register(Predicate<ItemStack> predicate, ItemSerializer serializer) {
		register(predicate, serializer, 0);
	}

	public void register(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority) {
		entries.add(new Entry(predicate, serializer, priority));
		entries.sort(Comparator.comparingInt(Entry::priority).reversed());
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

	private record Entry(Predicate<ItemStack> predicate, ItemSerializer serializer, int priority) {
	}
}
