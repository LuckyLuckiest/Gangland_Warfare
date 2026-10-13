package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * A civilian's 911 call landed: the held crime is committed with one witness (so it passes the gate), the squad is
 * sighted at the crime spot and the radio plays the Caller_911 line.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class NineOneOneListener implements Listener {

	public NineOneOneListener(CrimeService crimes, CopManager copManager, CopRadio copRadio, PlaceNames placeNames) {
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onCalledIn(CivilianCalledInEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
