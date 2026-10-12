package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.guard.TurfOwnerAlerts;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
public final class TurfOwnerAlertListener implements Listener {

	public TurfOwnerAlertListener(TurfOwnerAlerts alerts, TurfDefenderDeployer defenders, TurfPowerupManager powerups,
	                              TurfManager turfs, GangMembership gangs) {
	}

	@EventHandler
	public void onCivilianDeath(CivilianDeathEvent e) {
	}

	@EventHandler
	public void onDamage(EntityDamageByEntityEvent e) {
	}
}
