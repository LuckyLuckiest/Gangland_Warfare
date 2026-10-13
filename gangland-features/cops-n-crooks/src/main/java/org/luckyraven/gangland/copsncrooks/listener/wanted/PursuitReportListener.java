package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.BreakingNews;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Counts the chase's cop kills and cuffs, checks the breaking-news star trigger and prints the pursuit report when the chase
 * ends.
 *
 * @since 0.21.0
 */
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
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCuffed(CuffedEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** NORMAL: before ChaseArcListener (HIGH) ends the arc and HeatListener (MONITOR) clears the ledger. */
	@EventHandler(priority = EventPriority.NORMAL)
	public void onChaseEnd(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
