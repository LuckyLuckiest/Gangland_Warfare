package org.luckyraven.gangland.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ordered registry of {@link ItemRefresher} instances. Each call to {@link #refresh(ItemStack, Player)} walks the
 * registered refreshers and returns the first successful rebuild. If no refresher claims the item, a plain
 * {@link ItemStack#clone()} is returned so call-sites can always rely on getting a safe-to-deliver copy.
 *
 * <p>Entries are kept sorted by priority (highest first) using a <em>stable</em> sort, so within a priority tier
 * insertion order is preserved exactly — mirroring {@link ItemSerializerRegistry}. Today the core registers
 * {@code weaponRefresher} and {@code wearableRefresher} from the weapon module at priority {@code 10} (so they keep
 * outranking the core's {@code uniqueItemRefresher}, needed because a unique weapon carries both the weapon and the
 * uniqueItem NBT tag), while {@code ammunitionItemRefresher} registers at the default priority and so keeps sorting
 * behind {@code uniqueItemRefresher} — reproducing today's insertion order exactly. A catch-all refresher would opt
 * out of plain insertion order the same way the serializer registry's {@code MATERIAL} catch-all does, via
 * {@link #CATCH_ALL_PRIORITY}.
 */
public class ItemRefresherRegistry {

	/**
	 * Priority for a catch-all refresher that must always sort after every other registration, no matter how many
	 * modules register afterwards at the default priority.
	 */
	public static final int CATCH_ALL_PRIORITY = Integer.MIN_VALUE;

	private final List<Entry> entries = new ArrayList<>();

	public void register(ItemRefresher refresher) {
		register(refresher, 0);
	}

	public void register(ItemRefresher... items) {
		if (items == null) return;
		for (ItemRefresher refresher : items) register(refresher, 0);
	}

	public void register(ItemRefresher refresher, int priority) {
		if (refresher == null) return;
		entries.add(new Entry(refresher, priority));
		entries.sort(Comparator.comparingInt(Entry::priority).reversed());
	}

	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		if (source == null || source.getType().isAir()) return source;

		for (Entry entry : entries) {
			ItemRefresher refresher = entry.refresher();
			if (!refresher.canRefresh(source)) continue;

			ItemStack rebuilt = refresher.refresh(source, context);
			if (rebuilt != null) return rebuilt;
		}

		return source.clone();
	}

	/**
	 * Rebuilds display fields from config while preserving runtime state (durability, ammo, uses, enchants). Used by
	 * sell / trade-in flows where the item is flowing from the player to the trader and must not be reset.
	 */
	public ItemStack decorate(ItemStack source, @Nullable Player context) {
		if (source == null || source.getType().isAir()) return source;

		for (Entry entry : entries) {
			ItemRefresher refresher = entry.refresher();
			if (!refresher.canRefresh(source)) continue;

			ItemStack rebuilt = refresher.decorate(source, context);
			if (rebuilt != null) return rebuilt;
		}

		return source.clone();
	}

	private record Entry(ItemRefresher refresher, int priority) {
	}

}
