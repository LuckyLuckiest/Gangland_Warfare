package org.luckyraven.gangland.weapon.listener.fire;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.weapon.fire.PluginFireRegistry;

/**
 * Keeps every fire block placed by the weapon system purely cosmetic by cancelling the burn / spread / ignite events
 * that vanilla fire would otherwise fire on neighbouring flammable blocks. Natural fire (flint-and-steel, lava,
 * lightning) is unaffected — only events traceable to a block in {@link PluginFireRegistry} are suppressed.
 */
@ListenerHandler
@AutowireTarget({PluginFireRegistry.class})
public class PluginFireProtectionListener implements Listener {

	private final PluginFireRegistry registry;

	public PluginFireProtectionListener(PluginFireRegistry registry) {
		this.registry = registry;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBurn(BlockBurnEvent event) {
		if (registry.isTracked(event.getIgnitingBlock()) || registry.hasTrackedNeighbour(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSpread(BlockSpreadEvent event) {
		if (registry.isTracked(event.getSource())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onIgnite(BlockIgniteEvent event) {
		if (event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) return;
		if (registry.isTracked(event.getIgnitingBlock()) || registry.hasTrackedNeighbour(event.getBlock())) {
			event.setCancelled(true);
		}
	}

}
