package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;

/**
 * Starts the post-escape search when evasion takes the last star (0.16.1 T-187).
 * <p>
 * stub (0.16.1 T-187): not registered as a bean yet; the guard is the only logic here.
 */
public class PostEscapeListener implements Listener {

	private final PostEscapeSearch search;

	public PostEscapeListener(PostEscapeSearch search) {
		this.search = search;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		if (event.getCause() != WantedCause.EVASION || event.getNewLevel() != 0) return;

		search.begin(event.getPlayer(), event.getOldLevel());
	}
}
