package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.EvasionClock;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Ends the evasion tracking when the chase ends or the player leaves. */
@ListenerHandler
@RequiredArgsConstructor
public class EvasionListener implements Listener {

	private final EvasionClock clock;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		clock.clear(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		clock.clear(event.getPlayer());
	}
}
