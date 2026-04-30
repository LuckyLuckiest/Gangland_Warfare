package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
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
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

/**
 * One-shot chat feedback on {@link TurfEnterEvent} when a capture cannot start for the entering player — so they don't
 * stand in place wondering why nothing happens. Silent in every case where the player either can already capture, is on
 * defense, or has no gang (nothing to say to a gangless player).
 *
 * <p>Possible reasons reported:
 * <ul>
 *   <li><b>Cooldown</b> — turf was captured recently; tells them how many minutes until it's contestable.</li>
 *   <li><b>Protected</b> — owner gang still has members online (or within the post-logoff grace window).</li>
 * </ul>
 *
 * <p>An active contest no longer produces a "blocked" message — Phase 1 is global so any gang member who walks in can
 * help fill it, and the contest-aware action-bar / dual-bossbar UI already tells them what's happening. Sending a
 * "you're blocked" chat line on top would be wrong (they're not blocked) and noisy.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfCaptureFeedbackListener implements Listener {

	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final CaptureSettings     settings;
	private final TurfMessageContract messages;

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Turf   turf   = event.getTurf();
		Player player = event.getPlayer();
		long   now    = System.currentTimeMillis();

		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			// Gangless players can't capture anyway — nothing to explain.
			return;
		}
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId != null && ownerId == user.getGangId()) {
			// This is the player's own turf; "why isn't capture starting" isn't the question.
			return;
		}

		// Cooldown from a previous successful capture.
		long cooldownMs = settings.getCooldownMinutes() * 60_000L;
		long unlockAt   = turf.getLastCaptureTimestamp() + cooldownMs;
		if (now < unlockAt) {
			long seconds = Math.max(1L, (unlockAt - now + 999L) / 1000L);
			messages.send(player, "TURF_BLOCKED_COOLDOWN", "time", messages.formatDuration(seconds));
			return;
		}

		// An active contest is intentionally not reported here — see class javadoc for why.
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state != null && state.getState() == TurfState.CONTESTING) {
			return;
		}

		// Owner is still online or within the post-logoff grace period. Show the grace remaining so the player
		// understands it will eventually become capturable — without the countdown the message reads as a
		// permanent block and they think the system is broken.
		if (ownerId != null) {
			Gang owner = gangs.findById(ownerId);
			if (owner != null) {
				long graceMs = settings.getPostLogoffProtectionMinutes() * 60_000L;
				long since   = now - owner.getLastMemberOnlineAt();
				if (since <= graceMs) {
					long remaining = graceMs - since;
					long seconds   = Math.max(1L, (remaining + 999L) / 1000L);
					messages.send(player, "TURF_BLOCKED_PROTECTED",
					              "gang", GangDisplayNameResolver.resolve(owner),
					              "time", messages.formatDuration(seconds));
				}
			}
		}
	}
}
