package org.luckyraven.gangland.npcshops.listener.trader;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.npcshops.trader.ShopViewOpener;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * {@code condition = "isCitizensAvailable"} (D2/D-fix-1): this class's own {@code @EventHandler} parameter type is
 * Citizens' {@link NPCRightClickEvent}, so scanning/registering it on a Citizens-less server throws
 * {@code NoClassDefFoundError} out of the listener phase.
 */
@ListenerHandler(condition = "isCitizensAvailable")
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
