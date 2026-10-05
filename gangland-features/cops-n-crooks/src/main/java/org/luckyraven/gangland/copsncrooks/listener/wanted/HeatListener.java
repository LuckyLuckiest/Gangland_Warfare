package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Feeds the {@link HeatLedger}: crimes in, level changes and chase ends kept in step. */
@ListenerHandler
public class HeatListener implements Listener {

	private final HeatLedger ledger;

	public HeatListener(HeatLedger ledger) {
		this.ledger = ledger;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		ledger.record(event);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		ledger.onLevelChanged(event.getPlayer().getUniqueId(), event.getOldLevel(), event.getNewLevel(),
		                      event.getWanted().getMaxLevel(), event.getCause());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChaseEnd(WantedEndEvent event) {
		ledger.clear(event.getPlayer().getUniqueId());
	}
}
