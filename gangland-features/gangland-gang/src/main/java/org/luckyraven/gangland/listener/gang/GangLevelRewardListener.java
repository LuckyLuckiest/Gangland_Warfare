package org.luckyraven.gangland.listener.gang;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.gang.level.LevelRewardRunner;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Once a gang level-up stands (nobody cancelled it; the monitor runs last), hands the level reached to the reward
 * runner. Covers the automatic path (a free level) and {@code /glw gang upgrade confirm} alike.
 */
@ListenerHandler
public class GangLevelRewardListener implements Listener {

	private final LevelRewardRunner runner;

	public GangLevelRewardListener(LevelRewardRunner runner) {
		this.runner = runner;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onGangLevelUp(GangLevelUpEvent event) {
		if (event.getGang() == null) return;

		runner.onLevelReached(event.getGang(), event.getLevel().getLevelValue());
	}
}
