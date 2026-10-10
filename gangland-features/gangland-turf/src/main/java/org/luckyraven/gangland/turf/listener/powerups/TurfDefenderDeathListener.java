package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** T-251: tells the deployer which guards died, so only deaths count as "down". */
@ListenerHandler
public final class TurfDefenderDeathListener implements Listener {

	private final TurfDefenderDeployer defenders;

	public TurfDefenderDeathListener(TurfDefenderDeployer defenders) {
		this.defenders = defenders;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCivilianDeath(CivilianDeathEvent event) {
		defenders.markKilled(event.getCivilianNpc());
	}
}
