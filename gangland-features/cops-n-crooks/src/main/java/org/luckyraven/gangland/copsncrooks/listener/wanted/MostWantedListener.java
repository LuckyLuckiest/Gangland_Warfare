package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.mostwanted.MostWanted;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Tells {@link MostWanted} that a player left.
 *
 * @since 0.21.0
 */
@ListenerHandler
public class MostWantedListener implements Listener {

	private final MostWanted mostWanted;

	public MostWantedListener(MostWanted mostWanted) {
		this.mostWanted = mostWanted;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
	}
}
