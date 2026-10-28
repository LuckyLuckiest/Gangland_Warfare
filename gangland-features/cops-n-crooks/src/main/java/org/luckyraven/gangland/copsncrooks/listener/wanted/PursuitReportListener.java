package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.BreakingNews;
import org.luckyraven.gangland.copsncrooks.wanted.report.PursuitReport;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;
import java.util.UUID;

/**
 * Counts the chase's cop kills and cuffs, checks the breaking-news star trigger and prints the pursuit report when the chase
 * ends.
 *
 * @since 0.21.0
 */
@CustomLog
@ListenerHandler
public class PursuitReportListener implements Listener {

	private final ChaseArcs          arcs;
	private final HeatLedger         ledger;
	private final DetainmentRegistry registry;
	private final ChaseConfigLoader  config;
	private final WantedMessages     messages;
	private final BreakingNews       news;

	public PursuitReportListener(ChaseArcs arcs, HeatLedger ledger, DetainmentRegistry registry,
	                             ChaseConfigLoader config, WantedMessages messages, BreakingNews news) {
		this.arcs     = arcs;
		this.ledger   = ledger;
		this.registry = registry;
		this.config   = config;
		this.messages = messages;
		this.news     = news;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(CopDeathEvent event) {
		Player killer = event.getKiller();
		// 0.21.2 (T-545): a silent death counts when dispatch notices it and fires it again
		if (killer != null && !event.isSilent()) {
			arcs.copDown(killer.getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCuffed(CuffedEvent event) {
		arcs.bust(event.getTarget().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		if (event.getCause() == WantedCause.RESTORE || event.getNewLevel() == 0) {
			return;
		}
		news.check(event.getPlayer(), event.getNewLevel(), "STARS");
	}

	/** NORMAL: before ChaseArcListener (HIGH) ends the arc and HeatListener (MONITOR) clears the ledger. */
	@EventHandler(priority = EventPriority.NORMAL)
	public void onChaseEnd(WantedEndEvent event) {
		Player    player = event.getPlayer();
		UUID      id     = player.getUniqueId();
		ChaseArc  arc    = arcs.arc(id);
		WantedCause cause = event.getCause();
		if (arc == null || cause == WantedCause.RESTORE) {
			return;
		}

		ArrestOrigin origin = null;
		if (cause == WantedCause.ARREST) {
			DetainedPlayer row = registry.getDetainedPlayers().get(id);
			origin = row == null ? null : row.originOrDefault();
		}
		String ending = ChatColor.stripColor(messages.format(PursuitReport.ending(cause, origin), Map.of()));

		PursuitReportSettings settings = config.getPursuitReport();
		if (settings.enabled() && arc.peak() >= settings.minPeakStars()) {
			double heat = 0;
			for (CrimeRecord crime : ledger.chaseCrimes(id)) {
				heat += crime.heat();
			}
			long now = arcs.now();
			for (String line : PursuitReport.lines(arc, cause, origin, heat, settings, messages, now)) {
				player.sendMessage(line);
			}
			long   end     = arc.offlineAt() != 0 ? arc.offlineAt() : now;
			long   seconds = Math.max(0, (end - arc.startedAt()) / 1000L);
			double cost    = settings.perCopDown() * arc.copsDown() + settings.perHeat() * heat;
			log.debug("PURSUIT_REPORT {} ending={} peak={} seconds={} copsDown={} busts={} cost={}", player.getName(),
			          ending, arc.peak(), seconds, arc.copsDown(), arc.busts(), cost);
		}

		news.closing(player, arc, ending);
	}
}
