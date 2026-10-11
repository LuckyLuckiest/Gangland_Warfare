package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.function.ObjIntConsumer;

/** STUB (red phase). */
@ListenerHandler
public final class TurfDefenderDeathListener implements Listener {

	private final TurfDefenderDeployer defenders;

	public TurfDefenderDeathListener(TurfDefenderDeployer defenders, GangMembership gangs, MoneyAddon money,
	                                 MoneyDepositService deposits) {
		this.defenders = defenders;
	}

	void setDropper(ObjIntConsumer<Location> dropper) {
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCivilianDeath(CivilianDeathEvent event) {
		defenders.markKilled(event.getCivilianNpc());
	}
}
