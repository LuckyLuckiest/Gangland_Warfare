package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Clears a player's tracker seen-time and ping state when the chase ends or he leaves. */
@ListenerHandler
@RequiredArgsConstructor
public class TrackerListener implements Listener {

	private final TrackerTags tags;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
