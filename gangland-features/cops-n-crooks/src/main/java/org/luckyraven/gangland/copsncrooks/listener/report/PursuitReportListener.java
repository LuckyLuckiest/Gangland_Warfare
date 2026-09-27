package org.luckyraven.gangland.copsncrooks.listener.report;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.report.PursuitReportService;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Drives the 0.12 pursuit report ({@link PursuitReportService}): starts and updates a chase from the wanted
 * lifecycle, cop kills and cuffs, marks it {@code WASTED} on death/downed before {@code WantedLevelListener}'s
 * kill-combo death callback resets the wanted level, and reports/broadcasts it once {@code WantedEndEvent} fires.
 */
@ListenerHandler
public class PursuitReportListener implements Listener {

	private final PursuitReportService reportService;

	public PursuitReportListener(PursuitReportService reportService) {
		this.reportService = reportService;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		reportService.start(event.getPlayer(), event.getWantedLevel());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedLevelChange(WantedLevelChangeEvent event) {
		reportService.raise(event.getPlayer().getUniqueId(), event.getNewLevel());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(CopDeathEvent event) {
		reportService.copKilled(event.getKiller());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCuffed(CuffedEvent event) {
		reportService.markCuffed(event.getTarget().getUniqueId());
	}

	/**
	 * LOWEST: must record the wasted outcome before {@code WantedLevelListener}'s kill-combo death callback clears
	 * the wanted level and fires {@code WantedEndEvent}.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		reportService.markWasted(event.getEntity().getUniqueId());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDowned(PlayerDownedEvent event) {
		reportService.markWasted(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		reportService.finish(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		reportService.discard(player.getUniqueId());
	}

}
