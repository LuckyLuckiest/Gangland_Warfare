package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.copsncrooks.wanted.heat.ReportKind;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * A civilian's 911 call landed: {@link CopCrimeReports#landCivilian} commits the held crime with the call's viewers as
 * its witnesses (0.21.2 T-540), tips the squad to the crime spot unless a cop sees him now, drops it when he is
 * restrained by then, and has dispatch say Caller_911 about the crime spot (one landing path with the cop reports,
 * 0.21.2 review r2). A call whose criminal logged out is deferred to his next join, never committed while he is away
 * (0.21.2, T-551).
 *
 * @since 0.20.0
 */
@ListenerHandler
public class NineOneOneListener implements Listener {

	private final CopCrimeReports reports;

	public NineOneOneListener(CopCrimeReports reports) {
		this.reports = reports;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onCalledIn(CivilianCalledInEvent event) {
		Player criminal = event.getCriminal();
		if (event.isDeferred() || !criminal.isOnline()) {
			reports.defer(criminal, event.getCrimeId(), event.getAt(), event.getViewers(), ReportKind.CIVILIAN);
			return;
		}
		reports.landCivilian(criminal, event.getCrimeId(), event.getAt(), event.getViewers());
	}
}
