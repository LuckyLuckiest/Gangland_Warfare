package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.SightModel;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** A player's gunshot resets his {@link SightModel} factor for a few seconds (0.20.0 lane sight). TDD skeleton. */
@ListenerHandler
public class SightShotListener implements Listener {

	private final SightModel sightModel;

	public SightShotListener(SightModel sightModel) {
		this.sightModel = sightModel;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponShoot(WeaponShootEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
