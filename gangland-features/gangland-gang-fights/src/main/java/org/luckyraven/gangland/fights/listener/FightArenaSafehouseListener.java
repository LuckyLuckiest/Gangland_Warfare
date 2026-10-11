package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * No safehouse inside an arena: a gang could otherwise anchor a respawn point in the middle of a fight box. Any arena
 * counts, open or not, busy or idle. The safehouse command reports the refusal ({@code Safehouse.Set_Refused}).
 */
@ListenerHandler
public final class FightArenaSafehouseListener implements Listener {

	private final ArenaRegistry arenas;

	public FightArenaSafehouseListener(ArenaRegistry arenas) {
		this.arenas = arenas;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSafehouse(GangSafehouseSetEvent event) {
		if (arenas.at(event.location()).isPresent()) {
			event.setCancelled(true);
		}
	}
}
