package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
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
		// 0.21.2 (T-545): nobody knows of a silent death yet; it is fired again when dispatch notices it
		if (event.isSilent() || !(event.getNpc() instanceof CopNpc cop)) return;

		CopGroup group = cop.getGroup();
		if (group == null) return;

		UUID targetId = group.getTargetPlayerId();
		if (targetId == null) return;

		// a death dispatch noticed late has no body left: the spot he fell
		Entity   body = cop.getEntity();
		Location at   = body != null ? body.getLocation() : event.getAt();
		if (at == null) return;

		controller.recordCasualty(targetId, at);
	}
}
