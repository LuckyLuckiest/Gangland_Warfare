package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;

/**
 * A civilian's 911 call landed: the held crime is committed with one witness (so it passes the gate), the squad is
 * sighted at the crime spot and the radio plays the Caller_911 line.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class NineOneOneListener implements Listener {

	private final CrimeService crimes;
	private final CopManager   copManager;
	private final CopRadio     copRadio;
	private final PlaceNames   placeNames;

	public NineOneOneListener(CrimeService crimes, CopManager copManager, CopRadio copRadio, PlaceNames placeNames) {
		this.crimes     = crimes;
		this.copManager = copManager;
		this.copRadio   = copRadio;
		this.placeNames = placeNames;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onCalledIn(CivilianCalledInEvent event) {
		Player criminal = event.getCriminal();
		if (!criminal.isOnline()) return;

		// commit first: it can create the cop group that is looked up below
		if (!crimes.commit(criminal, event.getCrimeId(), event.getAt(), false, 1)) return;

		CopGroup group = copManager.groupOf(criminal.getUniqueId());
		if (group == null) return;

		group.getSquad().reportSighting(event.getAt());
		String place = placeNames.locate(event.getAt()).orElseGet(() -> copRadio.placeOf(event.getAt()));
		if (place == null || place.isBlank()) place = "the area";
		copRadio.sayFromLeader(group, "Caller_911", Map.of("crime", event.getCrimeId(), "place", place));
	}
}
