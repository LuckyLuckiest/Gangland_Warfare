package me.luckyraven.market.price;

import me.luckyraven.market.event.events.MarketPriceChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hops from the async price ticker back to the main thread to fire {@link MarketPriceChangedEvent}s. The ticker stages
 * a {@link PriceChangeBatch} then calls {@link #dispatch(PriceChangeBatch)}; this class schedules one main-thread task
 * that iterates the batch and calls {@link Bukkit#getPluginManager()}.
 *
 * <p>Only changes with an absolute delta greater than {@code threshold} are fired so listeners aren't spammed by
 * volatility noise.
 */
public final class PriceChangeDispatcher {

	private final JavaPlugin plugin;
	private final double     threshold;

	public PriceChangeDispatcher(JavaPlugin plugin, double threshold) {
		this.plugin    = plugin;
		this.threshold = threshold;
	}

	public void dispatch(PriceChangeBatch batch) {
		if (batch.isEmpty()) {
			return;
		}

		Bukkit.getScheduler().runTask(plugin, () -> {
			for (PriceChange change : batch.changes()) {
				if (Math.abs(change.delta()) < threshold) {
					continue;
				}
				Bukkit.getPluginManager().callEvent(new MarketPriceChangedEvent(change.itemId(),
				                                                                change.previousPrice(),
				                                                                change.newPrice()));
			}
		});
	}
}
