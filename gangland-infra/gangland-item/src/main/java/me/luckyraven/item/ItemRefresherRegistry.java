package me.luckyraven.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered registry of {@link ItemRefresher} instances. Each call to {@link #refresh(ItemStack, Player)} walks the
 * registered refreshers in insertion order and returns the first successful rebuild. If no refresher claims the item, a
 * plain {@link ItemStack#clone()} is returned so call-sites can always rely on getting a safe-to-deliver copy.
 */
public class ItemRefresherRegistry {

	private final List<ItemRefresher> refreshers = new ArrayList<>();

	public void register(ItemRefresher refresher) {
		if (refresher == null) return;
		refreshers.add(refresher);
	}

	public void register(ItemRefresher... items) {
		for (ItemRefresher refresher : items) register(refresher);
	}

	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		if (source == null || source.getType().isAir()) return source;

		for (ItemRefresher refresher : refreshers) {
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

		for (ItemRefresher refresher : refreshers) {
			if (!refresher.canRefresh(source)) continue;

			ItemStack rebuilt = refresher.decorate(source, context);
			if (rebuilt != null) return rebuilt;
		}

		return source.clone();
	}

}
