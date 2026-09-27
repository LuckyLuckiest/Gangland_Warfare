package org.luckyraven.gangland.copsncrooks.listener.evasion;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionService;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Drives line-of-sight evasion (0.12 F2):
 * <ul>
 *     <li>A chase starts tracking the player and ends (or a quit ends) tracking.</li>
 *     <li>The old repeating decay ({@code WantedExecutor}) fires {@link WantedEvent} before each step and skips the
 *     step when it is cancelled. For a player evasion handles, the step is cancelled, so the old timer only runs as
 *     the fallback for players no cop was ever sent after. With evasion disabled nothing is cancelled.</li>
 * </ul>
 */
@ListenerHandler
public class EvasionListener implements Listener {

	private final EvasionService evasionService;

	public EvasionListener(EvasionService evasionService) {
		this.evasionService = evasionService;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		evasionService.track(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		evasionService.untrack(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		evasionService.untrack(event.getPlayer().getUniqueId());
	}

	/**
	 * Fired asynchronously by the decay timer. The executor reuses one event instance for every step, so the flag is
	 * set on each delivery (not only when handled): a chase evasion stops handling, e.g. after Evasion.Enable is turned
	 * off, gets its fallback decay back instead of staying cancelled forever.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onWantedDecay(WantedEvent event) {
		event.setCancelled(evasionService.handles(event.getWanted().getOwner()));
	}
}
