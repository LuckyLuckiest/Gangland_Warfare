package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.events.vehicle.CarMoveEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

/**
 * Speeding pull-overs (0.20.0 lane patrol): a driver over the speed limit that a beat cop sees is ordered to stop.
 * Every move also feeds the driver's speed to the {@link PullOverTracker}.
 */
@ListenerHandler
public class PullOverListener implements Listener {

	private final PullOverTracker tracker;
	private final PatrolManager   patrol;
	private final TargetingManager targeting;
	private final CopLoader       copLoader;

	public PullOverListener(PullOverTracker tracker, PatrolManager patrol, TargetingManager targeting,
	                        CopLoader copLoader) {
		this.tracker   = tracker;
		this.patrol    = patrol;
		this.targeting = targeting;
		this.copLoader = copLoader;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCarMove(CarMoveEvent event) {
		Player driver = event.getDriver();
		UUID   id     = driver.getUniqueId();
		double speed  = event.getBlocksPerSecond();

		tracker.onSpeed(id, speed);

		PullOverSettings settings = copLoader.getLoadedProvider().getPullOverSettings();
		if (!settings.enabled() || speed <= settings.speedLimit()) {
			return;
		}
		if (!tracker.canOrder(id) || targeting.isWanted(id)) {
			return;
		}
		if (patrol.beatCopSees(driver, settings.range())) {
			tracker.order(driver, speed);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		tracker.forget(event.getPlayer().getUniqueId());
	}
}
