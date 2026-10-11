package org.luckyraven.gangland.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.npc.QuartermasterNameplate;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Keeps the Quartermaster's owner-tag line in step with the turf owner. No Citizens type in any signature. */
@ListenerHandler
@RequiredArgsConstructor
public final class QuartermasterNameplateListener implements Listener {

	private final QuartermasterNameplate nameplates;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		nameplates.refresh(event.getTurf().getId());
	}
}
