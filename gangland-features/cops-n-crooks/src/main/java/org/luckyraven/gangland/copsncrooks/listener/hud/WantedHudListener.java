package org.luckyraven.gangland.copsncrooks.listener.hud;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.events.evasion.EvasionStateChangeEvent;
import org.luckyraven.gangland.copsncrooks.hud.WantedHudService;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Drives the wanted HUD (0.12 F3): shows/refreshes the boss bar on wanted start and every evasion state change,
 * fires the star gained/lost feedback by comparing {@link WantedLevelChangeEvent} levels, and hides the HUD when the
 * chase ends or the player is no longer around to see it.
 */
@ListenerHandler
public class WantedHudListener implements Listener {

	private final WantedHudService hudService;

	public WantedHudListener(WantedHudService hudService) {
		this.hudService = hudService;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		hudService.onStarGained(event.getPlayer(), event.getWantedLevel(), event.getWanted().getMaxLevel());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedLevelChange(WantedLevelChangeEvent event) {
		if (event.getNewLevel() <= 0) return; // WantedEndEvent hides the HUD
		// A 0 -> n start is handled by onWantedStart above. This event also fires (before Wanted.setLevel actually
		// applies the new level), so handling it here too would both double the star-gain title/sound and read the
		// still-old level/wanted state through show()/refresh().
		if (event.getOldLevel() == 0) return;

		Player player   = event.getPlayer();
		int    maxLevel = event.getWanted().getMaxLevel();

		if (event.getNewLevel() > event.getOldLevel()) {
			hudService.onStarGained(player, event.getNewLevel(), maxLevel);
		} else if (event.getNewLevel() < event.getOldLevel()) {
			hudService.onStarLost(player, event.getNewLevel(), maxLevel);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEvasionStateChange(EvasionStateChangeEvent event) {
		// refreshIfShown, not show(): EvasionService#untrack fires this synchronously from within WantedEndEvent /
		// PlayerQuitEvent handling (see EvasionListener), which can run before or after this listener's own
		// onWantedEnd/onPlayerQuit at the same priority. Calling show() here would recreate a bar for a player whose
		// HUD this listener already just hid, on the ordering where EvasionListener's handler runs second.
		hudService.refreshIfShown(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		hudService.hide(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		hudService.hide(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		hudService.hide(event.getEntity());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDowned(PlayerDownedEvent event) {
		hudService.hide(event.getPlayer());
	}
}
