package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;

/**
 * Turns capture lifecycle events into user-facing messages — private warnings to the defender gang on contest start and
 * the 50% milestone, and a server-wide broadcast when the capture completes (gated by
 * {@code Capture.Broadcast_Globally}). Failures stay silent per spec §2.6.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfCaptureNotifier implements Listener {

	private static final double HALF_MILESTONE = 50.0;

	private final TurfMessageContract messages;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final CaptureSettings     settings;

	@EventHandler
	public void onCaptureStart(TurfCaptureStartEvent event) {
		Integer defenderGangId = event.getTurf().getOwnerGangId();
		if (defenderGangId == null) {
			return;
		}
		notifyDefenders(defenderGangId, "TURF_CONTEST_START_DEFENDER",
		                "turf", event.getTurf().getDisplayName(),
		                "gang", GangDisplayNameResolver.resolve(event.getChallengerGang()));
	}

	@EventHandler
	public void onCaptureProgress(TurfCaptureProgressEvent event) {
		// Spec §2.6: second defender warning fires on the 50% milestone crossing. CaptureService already
		// dedupes upward crossings, so this handler only needs to pick the event that represents the 50%
		// bracket — any progress in [50, 75) is the 50% crossing (75 is its own milestone).
		double progress = event.getProgress();
		if (progress < HALF_MILESTONE || progress >= 75.0) {
			return;
		}
		Integer defenderGangId = event.getTurf().getOwnerGangId();
		if (defenderGangId == null) {
			return;
		}
		notifyDefenders(defenderGangId, "TURF_CONTEST_HALF_DEFENDER",
		                "turf", event.getTurf().getDisplayName());
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		if (!settings.isBroadcastCaptureGlobally()) {
			return;
		}
		String oldName = event.getOldOwner() == null ?
		                 "Unclaimed" :
		                 GangDisplayNameResolver.resolve(event.getOldOwner());
		messages.broadcast("TURF_CAPTURED_BROADCAST",
		                   "turf", event.getTurf().getDisplayName(),
		                   "gang", GangDisplayNameResolver.resolve(event.getNewOwner()),
		                   "old_gang", oldName);
	}

	private void notifyDefenders(int defenderGangId, String key, Object... replacements) {
		Gang defender = gangs.findById(defenderGangId);
		if (defender == null) {
			return;
		}
		for (Player online : Bukkit.getOnlinePlayers()) {
			User<Player> user = users.findByPlayer(online);
			if (user == null || !user.hasGang() || user.getGangId() != defenderGangId) {
				continue;
			}
			messages.send(online, key, replacements);
		}
	}
}
