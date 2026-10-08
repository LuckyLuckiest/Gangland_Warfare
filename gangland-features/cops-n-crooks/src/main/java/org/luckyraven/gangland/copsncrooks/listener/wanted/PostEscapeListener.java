package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Starts the post-escape search when evasion takes the last star (0.16.1 T-187). The one predicate that decides an escape
 * is {@link PostEscapeSearch#isEscape}, so the HUD, the cops and this listener agree.
 *
 * @since 0.16.1
 */
@ListenerHandler
public class PostEscapeListener implements Listener {

	private final PostEscapeSearch search;

	public PostEscapeListener(PostEscapeSearch search) {
		this.search = search;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		if (event.getNewLevel() != 0 || !search.isEscape(event.getCause())) return;

		search.begin(event.getPlayer(), event.getOldLevel());
	}
}
