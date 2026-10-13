package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Snapshots the suspect description on every crime and forgets it when the chase ends (0.20.0 lane disguise). */
@ListenerHandler
public class SuspectListener implements Listener {

	public SuspectListener(SuspectDescriptions descriptions, PoliceOutfit outfit, DisguiseTick tick) {
	}

	@EventHandler
	public void onCrime(CrimeCommittedEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler
	public void onChaseEnd(WantedEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
