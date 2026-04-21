package me.luckyraven.copsncrooks.listener.trader;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.ShopViewOpener;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.core.bean.listener.ListenerHandler;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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
