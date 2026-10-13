package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescription;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** Snapshots the suspect description on every crime and forgets it when the chase ends (0.20.0 lane disguise). */
@CustomLog
@ListenerHandler
public class SuspectListener implements Listener {

	private final SuspectDescriptions descriptions;
	private final PoliceOutfit        outfit;
	private final DisguiseTick        tick;

	public SuspectListener(SuspectDescriptions descriptions, PoliceOutfit outfit, DisguiseTick tick) {
		this.descriptions = descriptions;
		this.outfit       = outfit;
		this.tick         = tick;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		Player player = event.getPlayer();

		SuspectDescription stored = descriptions.describe(player);
		descriptions.store(player.getUniqueId(), stored);
		outfit.onCrime(player);

		log.debug("SUSPECT {} desc={}", player.getName(), descriptions.format(stored));
	}

	@EventHandler
	public void onChaseEnd(WantedEndEvent event) {
		Player player = event.getPlayer();

		descriptions.clear(player.getUniqueId());
		outfit.clear(player.getUniqueId());
		tick.forget(player.getUniqueId());
	}
}
