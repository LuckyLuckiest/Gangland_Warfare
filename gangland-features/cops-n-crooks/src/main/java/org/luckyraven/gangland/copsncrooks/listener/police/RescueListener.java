package org.luckyraven.gangland.copsncrooks.listener.police;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.rescue.RescueService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Feeds right-clicks on NPCs to the {@link RescueService}. Citizens-typed handler, hence the condition.
 * TDD skeleton.
 *
 * @since 0.21.0
 */
@ListenerHandler(condition = "isCitizensAvailable")
public class RescueListener implements Listener {

	public RescueListener(RescueService rescueService) {
	}

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
