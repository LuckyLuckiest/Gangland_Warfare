package me.luckyraven.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.entity.EntityMark;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;

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
