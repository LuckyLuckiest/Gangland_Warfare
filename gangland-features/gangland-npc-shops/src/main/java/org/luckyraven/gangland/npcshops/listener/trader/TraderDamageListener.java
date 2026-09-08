package org.luckyraven.gangland.npcshops.listener.trader;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

@ListenerHandler
@RequiredArgsConstructor
public class TraderDamageListener implements Listener {

	private final TraderManager traderManager;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onTraderDeath(EntityDeathEvent event) {
		TraderNpc trader = traderManager.getByEntity(event.getEntity());
		if (trader == null) return;

		UUID traderId = trader.getData().getId();
		traderManager.onTraderKilled(traderId);
	}

}
