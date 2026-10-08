package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

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

	/**
	 * Notes the chase's peak star count while he is wanted; the drop to 0 takes it. The escape is paid on that peak, so a
	 * 5-star chase that ends by evasion is not priced like a 1-star one (0.16.1 wanted-3).
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		if (event.getNewLevel() > 0) {
			search.recordLevel(id, event.getNewLevel());
			return;
		}

		int peak = search.takePeak(id, event.getOldLevel());
		if (!search.isEscape(event.getCause())) return;

		search.begin(event.getPlayer(), peak);
	}
}
