package me.luckyraven.turf.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.turf.events.TurfEnterEvent;
import me.luckyraven.turf.events.TurfExitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Shows an action-bar line when a player enters a turf — gang-owned turfs get the owning gang's color + name, unclaimed
 * turfs get a neutral label. Exit clears the action bar.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfActionBarListener implements Listener {

	private final GangLookupContract gangs;

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Integer ownerId = event.getTurf().getOwnerGangId();
		String  label;
		if (ownerId == null) {
			label = "&8» &7Entering &f" + event.getTurf().getDisplayName() + " &7— &8Unclaimed territory";
		} else {
			Gang   owner    = gangs.findById(ownerId);
			String gangName = owner == null ? "Unknown" : owner.getDisplayName();
			label = "&8» &7Entering &f" + event.getTurf().getDisplayName() + " &7— Controlled by &e" + gangName;
		}
		ActionBarManager.send(event.getPlayer(), ChatUtil.color(label));
	}

	@EventHandler
	public void onExit(TurfExitEvent event) {
		ActionBarManager.send(event.getPlayer(), "");
	}
}
