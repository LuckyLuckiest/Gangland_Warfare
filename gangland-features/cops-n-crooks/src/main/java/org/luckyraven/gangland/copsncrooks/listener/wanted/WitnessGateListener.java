package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Holds a gated crime (Wanted.Witnesses.Crimes) that no cop and no witness saw: the event is cancelled, so the heat
 * ledger and the chase arc skip it, and a civilian who saw it is asked to call 911.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class WitnessGateListener implements Listener {

	public WitnessGateListener(ChaseConfigLoader config, WitnessService witnesses, CopManager copManager) {
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
