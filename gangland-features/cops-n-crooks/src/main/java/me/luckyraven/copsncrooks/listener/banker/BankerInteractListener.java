package me.luckyraven.copsncrooks.listener.banker;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerManager;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.view.BankerViewOpener;
import me.luckyraven.util.listener.ListenerHandler;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@ListenerHandler
@RequiredArgsConstructor
public class BankerInteractListener implements Listener {

	private final BankerManager    bankerManager;
	private final BankerViewOpener viewOpener;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		BankerNpc banker = bankerManager.getByEntity(entity);
		if (banker == null) return;

		Player player = event.getClicker();
		viewOpener.openFor(player, banker);
	}

}
