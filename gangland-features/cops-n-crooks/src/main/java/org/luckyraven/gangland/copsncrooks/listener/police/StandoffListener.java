package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.events.npc.CopDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.standoff.StandoffController;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

/**
 * Feeds cop deaths to the standoff's man-down trigger (0.21.0).
 *
 * @since 0.21.0
 */
@ListenerHandler
public class StandoffListener implements Listener {

	private final StandoffController controller;

	public StandoffListener(StandoffController controller) {
		this.controller = controller;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(CopDeathEvent event) {
		if (!(event.getNpc() instanceof CopNpc cop)) return;

		CopGroup group = cop.getGroup();
		if (group == null) return;

		UUID targetId = group.getTargetPlayerId();
		if (targetId == null) return;

		Entity body = cop.getEntity();
		if (body == null) return;

		controller.recordCasualty(targetId, body.getLocation());
	}
}
