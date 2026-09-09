package org.luckyraven.gangland.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

/**
 * Prevents Gangland NPCs (civilians, cops) from being teleported through nether/end portals. Citizens pathfinding can
 * route NPCs through portal blocks to reach a valid destination — this listener cancels the teleport so the NPC stays
 * in its original world.
 */
@ListenerHandler
@RequiredArgsConstructor
public class NpcPortalListener implements Listener {

	private final NpcMarkManager markManager;

	@EventHandler(ignoreCancelled = true)
	public void onEntityPortal(EntityPortalEvent event) {
		Entity     entity = event.getEntity();
		EntityMark mark   = EntityMarks.of(markManager.getMark(entity));

		if (mark.isCivilian()) {
			event.setCancelled(true);
		}
	}
}
