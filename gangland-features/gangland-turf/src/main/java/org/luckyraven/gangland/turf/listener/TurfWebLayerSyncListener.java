package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.map.web.TurfWebLayer;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Keeps the web-map layer in step with ownership. Every owner change (capture, admin set owner, gang disband,
 * inactivity release) fires {@link TurfOwnerChangedEvent}. Turf creation and deletion fire no event, so the layer's
 * periodic reconcile adds new markers and drops deleted ones; the remove branch below only covers an owner change on
 * a turf already gone from the manager.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfWebLayerSyncListener implements Listener {

	private final TurfWebLayer layer;
	private final TurfManager  turfs;

	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		int id = event.getTurf().getId();
		if (turfs.get(id) == null) {
			layer.remove(id);
		} else {
			layer.sync(event.getTurf());
		}
	}
}
