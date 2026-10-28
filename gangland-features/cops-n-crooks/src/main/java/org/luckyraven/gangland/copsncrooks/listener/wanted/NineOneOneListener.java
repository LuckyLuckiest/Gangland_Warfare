package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.heat.ReportKind;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;

/**
 * A civilian's 911 call landed: the held crime is committed with the call's viewers as its witnesses (at least one, so
 * it passes the gate; 0.21.2 T-540 scores the head count), the squad is
 * tipped off to the crime spot and dispatch radios the Caller_911 line. Dispatch needs no speaker, so the line plays
 * even when the commit has just created the group and no cop has spawned yet (0.21.1, T-345). A call whose criminal
 * logged out is deferred to his next join, never committed while he is away (0.21.2, T-551).
 *
 * @since 0.20.0
 */
@ListenerHandler
public class NineOneOneListener implements Listener {

	private final CrimeService crimes;
	private final CopManager   copManager;
	private final CopRadio     copRadio;
	private final PlaceNames     placeNames;
	private final WantedMessages messages;
	private final HeatLedger     ledger;
	private final CopCrimeReports reports;

	public NineOneOneListener(CrimeService crimes, CopManager copManager, CopRadio copRadio, PlaceNames placeNames,
	                          WantedMessages messages, HeatLedger ledger, CopCrimeReports reports) {
		this.crimes     = crimes;
		this.copManager = copManager;
		this.copRadio   = copRadio;
		this.placeNames = placeNames;
		this.messages   = messages;
		this.ledger     = ledger;
		this.reports    = reports;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onCalledIn(CivilianCalledInEvent event) {
		Player criminal = event.getCriminal();
		if (event.isDeferred() || !criminal.isOnline()) {
			reports.defer(criminal, event.getCrimeId(), event.getAt(), event.getViewers(),
			              ReportKind.CIVILIAN);
			return;
		}

		// commit first: it can create the cop group that is looked up below. Inside commitReported, so the star card says
		// a witness reported it (0.21.1 Q7)
		boolean committed = ledger.commitReported(criminal, ReportKind.CIVILIAN,
		                                          () -> crimes.commit(criminal, event.getCrimeId(), event.getAt(), false,
		                                                              event.getViewers()));
		if (!committed) return;

		CopGroup group = copManager.groupOf(criminal.getUniqueId());
		if (group == null) return;

		// the caller's report is a tip to the crime spot, not a sighting: the squad searches there (R4)
		group.getSquad().reportTip(event.getAt());
		String place = placeNames.locate(event.getAt()).orElseGet(() -> copRadio.placeOf(event.getAt()));
		if (place == null || place.isBlank()) place = "the area";
		String crime = messages.crimeName(event.getCrimeId());
		copRadio.dispatch(group, criminal, "Caller_911", 0, "", Map.of("crime", crime, "place", place));
	}
}
