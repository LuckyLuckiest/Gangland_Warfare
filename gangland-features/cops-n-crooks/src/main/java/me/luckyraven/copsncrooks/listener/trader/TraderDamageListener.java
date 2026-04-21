package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.core.bean.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

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
