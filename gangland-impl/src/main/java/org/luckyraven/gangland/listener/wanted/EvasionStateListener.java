package org.luckyraven.gangland.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Feeds {@link ContactDesk} the evasion state cops-n-crooks publishes, so the core knows who is in a cop's view. */
@ListenerHandler
public final class EvasionStateListener implements Listener {

	private final ContactDesk desk;

	public EvasionStateListener(ContactDesk desk) {
		this.desk = desk;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onState(WantedEvasionStateEvent event) {
		desk.observe(event.getPlayer().getUniqueId(), event.getState());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		desk.forget(event.getPlayer().getUniqueId());
	}

}
