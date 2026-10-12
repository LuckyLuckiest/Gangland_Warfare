package org.luckyraven.gangland.turf.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.turf.map.MapPrefsService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Forgets a player's map preferences when they quit, so the in-memory state never outlives the session.
 */
@ListenerHandler
public final class MapPrefsQuitListener implements Listener {

	private final MapPrefsService prefs;

	public MapPrefsQuitListener(MapPrefsService prefs) {
		this.prefs = prefs;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		prefs.clear(event.getPlayer().getUniqueId());
	}
}
