package org.luckyraven.gangland.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMark;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMarkManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Prevents Gangland NPCs (civilians, cops) from being teleported through nether/end portals. Citizens pathfinding can
 * route NPCs through portal blocks to reach a valid destination — this listener cancels the teleport so the NPC stays
 * in its original world.
 */
@ListenerHandler
@RequiredArgsConstructor
public class NpcPortalListener implements Listener {

	private final EntityMarkManager entityMarkManager;

	@EventHandler(ignoreCancelled = true)
	public void onEntityPortal(EntityPortalEvent event) {
		Entity     entity = event.getEntity();
		EntityMark mark   = entityMarkManager.getEntityMark(entity);

		if (mark.isCivilian()) {
			event.setCancelled(true);
		}
	}
}
