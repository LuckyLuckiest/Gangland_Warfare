package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Holds a gated crime (Wanted.Witnesses.Crimes) until someone who saw it reports it: the event is cancelled, so the heat
 * ledger and the chase arc skip it, and {@link CopCrimeReports#open} looks for its reporter - a cop eyewitness first,
 * then a civilian's 911 call. A report lands as a recommit with a cop's flag or a witness count, which passes here.
 *
 * <p>0.21.2 (T-541, PLAN.md 1.3): no bypass any more. A fresh squad sighting or a cop who merely has the player in
 * view no longer lets a gated crime through in the same tick: a cop who saw it radios it in after Report_Seconds.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class WitnessGateListener implements Listener {

	private final ChaseConfigLoader config;
	private final CopCrimeReports   reports;
	private final HeatLedger        ledger;

	public WitnessGateListener(ChaseConfigLoader config, CopCrimeReports reports, HeatLedger ledger) {
		this.config  = config;
		this.reports = reports;
		this.ledger  = ledger;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		if (!NpcSupport.available()) return;

		WitnessSettings settings = config.getWitnesses();
		if (!settings.enabled() || !settings.crimes().contains(event.getCrimeId())) return;
		if (event.isSeenByCop() || event.getWitnesses() > 0) return;

		event.setCancelled(true);
		// the cop an Assault_Cop / Kill_Cop hit, so a kill supersedes his pending assault report
		reports.open(event.getPlayer(), event.getCrimeId(), event.getLocation(), ledger.currentVictim());
	}
}
