package org.luckyraven.gangland.npcshops.listener.banker;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.npcshops.banker.BankerManager;
import org.luckyraven.gangland.npcshops.banker.BankerNpc;
import org.luckyraven.gangland.npcshops.banker.view.BankerFlow;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * {@code condition = "isCitizensAvailable"} (D2/D-fix-1): this class's own {@code @EventHandler} parameter type is
 * Citizens' {@link NPCRightClickEvent}, so scanning/registering it on a Citizens-less server throws
 * {@code NoClassDefFoundError} out of the listener phase.
 */
@ListenerHandler(condition = "isCitizensAvailable")
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
