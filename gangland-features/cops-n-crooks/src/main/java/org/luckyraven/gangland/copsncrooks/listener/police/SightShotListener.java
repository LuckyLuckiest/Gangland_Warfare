package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.SightModel;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/** A player's gunshot resets his {@link SightModel} factor for a few seconds (0.20.0 lane sight). */
@ListenerHandler
public class SightShotListener implements Listener {

	private final SightModel sightModel;

	public SightShotListener(SightModel sightModel) {
		this.sightModel = sightModel;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponShoot(WeaponShootEvent event) {
		if (event.getShooter() instanceof Player shooter && !NpcSupport.isNpc(shooter)) {
			sightModel.shot(shooter);
		}
	}
}
