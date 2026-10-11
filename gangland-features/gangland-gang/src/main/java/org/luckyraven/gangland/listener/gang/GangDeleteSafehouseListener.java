package org.luckyraven.gangland.listener.gang;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** A disbanded gang leaves no safehouse rows and no open rally call behind. */
@ListenerHandler
public class GangDeleteSafehouseListener implements Listener {

	private final GangSafehouseService safehouseService;

	public GangDeleteSafehouseListener(GangSafehouseService safehouseService) {
		this.safehouseService = safehouseService;
	}

	@EventHandler
	public void onGangDelete(GangDeleteEvent event) {
		safehouseService.removeAll(event.getGang().getId());
	}
}
