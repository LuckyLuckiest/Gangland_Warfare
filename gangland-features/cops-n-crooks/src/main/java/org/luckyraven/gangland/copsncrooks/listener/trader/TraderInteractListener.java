package org.luckyraven.gangland.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.npc.trader.ShopViewOpener;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;

@ListenerHandler
@RequiredArgsConstructor
public class TraderInteractListener implements Listener {

	private final TraderManager  traderManager;
	private final ShopViewOpener viewOpener;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		TraderNpc trader = traderManager.getByEntity(entity);
		if (trader == null) return;

		Player player = event.getClicker();
		viewOpener.openFor(player, trader);
	}

}
