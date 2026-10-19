package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Pays the getaway at WantedEndEvent LOW (arc alive) and prints the line at MONITOR, after the pursuit report.
 *
 * @since 0.21.0
 */
@CustomLog
@ListenerHandler
public class GetawayListener implements Listener {

	public GetawayListener(GetawayService service, ChaseArcs arcs, WantedMessages messages) {
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEndAward(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEndSend(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
