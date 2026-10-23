package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Plain Bukkit events that end or hand over a 911 call: the caller dies, the criminal quits.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class WitnessListener implements Listener {

	private final CivilianService civilians;
	private final WitnessService  witnesses;

	public WitnessListener(CivilianService civilians, WitnessService witnesses) {
		this.civilians = civilians;
		this.witnesses = witnesses;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCivilianDeath(EntityDeathEvent event) {
		CivilianNpc npc = civilians.getNpc(event.getEntity().getUniqueId());
		if (npc != null && npc.getWitnessCall() != null) witnesses.onCallerDeath(npc);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		witnesses.onCriminalQuit(event.getPlayer().getUniqueId());
	}
}
