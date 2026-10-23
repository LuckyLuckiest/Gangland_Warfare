package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Holds a gated crime (Wanted.Witnesses.Crimes) that no cop and no witness saw: the event is cancelled, so the heat
 * ledger and the chase arc skip it, and a civilian who saw it is asked to call 911.
 *
 * @since 0.20.0
 */
@CustomLog
@ListenerHandler
public class WitnessGateListener implements Listener {

	private final ChaseConfigLoader    config;
	private final WitnessService       witnesses;
	private final CopManager           copManager;

	public WitnessGateListener(ChaseConfigLoader config, WitnessService witnesses, CopManager copManager) {
		this.config     = config;
		this.witnesses  = witnesses;
		this.copManager = copManager;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		if (!NpcSupport.available()) return;

		WitnessSettings settings = config.getWitnesses();
		if (!settings.enabled() || !settings.crimes().contains(event.getCrimeId())) return;
		if (event.isSeenByCop() || event.getWitnesses() > 0) return;

		CopGroup group = copManager.groupOf(event.getPlayer().getUniqueId());
		if (group != null && group.getSquad().hasFreshSighting()) return;

		event.setCancelled(true);
		int callers = witnesses.witness(event.getPlayer(), event.getCrimeId(), event.getLocation());
		log.debug("WITNESS {} crime={} callers={}", event.getPlayer().getName(), event.getCrimeId(), callers);
	}
}
