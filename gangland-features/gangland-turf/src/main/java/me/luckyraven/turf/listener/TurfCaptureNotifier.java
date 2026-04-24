package me.luckyraven.turf.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.capture.CaptureSettings;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.events.TurfCaptureProgressEvent;
import me.luckyraven.turf.events.TurfCaptureStartEvent;
import me.luckyraven.turf.events.TurfCapturedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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
