package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.events.vehicle.CarMoveEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Speeding pull-overs (0.20.0 lane patrol): a driver over the speed limit that a beat cop sees is ordered to stop.
 * Every move also feeds the driver's speed to the {@link PullOverTracker}.
 */
@ListenerHandler
public class PullOverListener implements Listener {

	public PullOverListener(PullOverTracker tracker, PatrolManager patrol, TargetingManager targeting,
	                        CopLoader copLoader) {
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCarMove(CarMoveEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
