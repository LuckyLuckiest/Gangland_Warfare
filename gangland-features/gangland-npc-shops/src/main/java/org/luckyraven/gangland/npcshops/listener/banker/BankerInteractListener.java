package org.luckyraven.gangland.copsncrooks.listener.banker;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerNpc;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.BankerFlow;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
@RequiredArgsConstructor
public class BankerInteractListener implements Listener {

	private final BankerManager bankerManager;
	private final BankerFlow    bankerFlow;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		BankerNpc banker = bankerManager.getByEntity(entity);
		if (banker == null) return;

		Player player = event.getClicker();
		bankerFlow.start(player, banker);
	}

}
