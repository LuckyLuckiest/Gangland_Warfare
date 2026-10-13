package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Crew heat (0.21.0, P-CREW-1..6): a crime is recorded in each online gang mate's or ally's own heat ledger.
 *
 * @since 0.21.0
 */
@ListenerHandler
public class CrewHeatListener implements Listener {

	public CrewHeatListener(HeatLedger ledger, GangMembership gangs, TurfManager turfs,
	                        DetainmentService detainmentService, ChaseConfigLoader config, WantedMessages messages) {
		// TDD stub
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
