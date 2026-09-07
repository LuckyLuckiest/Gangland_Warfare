package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;

/**
 * Plays the configured "unclaimed" sound to every player currently standing inside a turf when its owning gang is
 * cleared (admin {@code /glw turf setowner none}, inactivity auto-release, or any other non-capture flow that nulls the
 * owner). Capture-failed and capture-complete already get their own SFX through {@code CaptureService}, so this
 * listener only reacts to the owned→unclaimed transition that fires {@link TurfOwnerChangedEvent}.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfOwnerSoundListener implements Listener {

	private final TurfManager       turfs;
	private final TurfSoundContract sounds;

	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		if (event.getNewOwnerGangId() != null) {
			return; // Only the owned→unclaimed transition gets the audio cue.
		}
		int turfId = event.getTurf().getId();
		for (Player online : Bukkit.getOnlinePlayers()) {
			var at = turfs.findAt(online.getLocation());
			if (at != null && at.getId() == turfId) {
				sounds.playOwnerCleared(online);
			}
		}
	}
}
