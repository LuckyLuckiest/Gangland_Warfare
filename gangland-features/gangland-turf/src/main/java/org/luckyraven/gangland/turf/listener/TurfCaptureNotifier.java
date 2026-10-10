package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfDefenderDownEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Turf alerts that reach a gang wherever its members are online (any world). Four kinds:
 *
 * <ul>
 *   <li><b>Start</b> - owners get the attacker count and side ("3 Rats from the NE"); allies of the owner get the
 *   start line only. Unclaimed turfs stay silent.</li>
 *   <li><b>Half the guards down</b> - owners, once per contest, on the first defender-down event with
 *   {@code remaining * 2 <= deployed}.</li>
 *   <li><b>Last stand</b> - owners, once per contest, when progress crosses 75.</li>
 *   <li><b>Outcome</b> - owners and attackers, always sent: held/attack failed for DEFENDED and ABANDONED,
 *   stalled for STALLED, lost/taken for a completed capture. CANCELLED (an admin action) tells nobody.</li>
 * </ul>
 *
 * <p>Kinds one to three are throttled to one line per turf per player per {@link #THROTTLE_MS} milliseconds. Per-contest
 * flags reset on a new start and on every outcome. The server-wide capture broadcast stays gated by
 * {@code Capture.Broadcast_Globally}.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfCaptureNotifier implements Listener {

	private static final long   THROTTLE_MS  = 10_000L;
	private static final double LAST_STAND   = 75.0;

	private final TurfMessageContract messages;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final CaptureSettings     settings;
	private final TurfMessages        text;
	private final TurfManager         turfs;

	// ponytail: lastSent keeps one entry per player per turf touched and is never pruned; add a logout prune if it grows.
	private final Map<String, Long> lastSent           = new HashMap<>();
	private final Set<Integer>      halfAnnounced      = new HashSet<>();
	private final Set<Integer>      lastStandAnnounced = new HashSet<>();

	private LongSupplier clock = System::currentTimeMillis;

	void setClock(LongSupplier clock) {
		this.clock = clock;
	}

	@EventHandler
	public void onCaptureStart(TurfCaptureStartEvent event) {
		Turf turf = event.getTurf();
		int turfId = turf.getId();
		clearContest(turfId);

		Integer ownerId = turf.getOwnerGangId();
		Gang owner = ownerId == null ? null : gangs.findById(ownerId);
		if (owner == null) {
			return;
		}

		Gang challenger = event.getChallengerGang();
		int count = Math.max(1, challengerCount(turfId, challenger.getId()));
		String direction = direction(turf, challenger);
		String turfName = turf.getDisplayName();
		String challengerName = GangDisplayNameResolver.resolve(challenger);
		String ownerName = GangDisplayNameResolver.resolve(owner);

		String ownerLine = direction.isEmpty() ?
		                   text.text("Alerts.Start_No_Side", "&c%turf% is under attack! &f%count% %gang%",
		                             "turf", turfName, "count", count, "gang", challengerName) :
		                   text.text("Alerts.Start", "&c%turf% is under attack! &f%count% %gang% &cfrom the %direction%",
		                             "turf", turfName, "count", count, "gang", challengerName,
		                             "direction", direction);
		String allyLine = text.text("Alerts.Start_Ally", "&e%turf% &7(%owner%) &eis under attack by %gang%",
		                            "turf", turfName, "owner", ownerName, "gang", challengerName);

		for (Player online : Bukkit.getOnlinePlayers()) {
			Integer memberOf = gangIdOf(online);
			if (memberOf == null) {
				continue;
			}
			if (memberOf == owner.getId()) {
				sendThrottled(online, turfId, ownerLine);
			} else if (isAllyOf(memberOf, owner)) {
				sendThrottled(online, turfId, allyLine);
			}
		}
	}

	@EventHandler
	public void onDefenderDown(TurfDefenderDownEvent event) {
		int turfId = event.turfId();
		Turf turf = turfs.get(turfId);
		if (turf == null || turf.getOwnerGangId() == null) {
			return;
		}
		if (event.remaining() * 2 > event.deployed()) {
			return;
		}
		if (!halfAnnounced.add(turfId)) {
			return;
		}
		String line = text.text("Alerts.Half_Guards", "&eHalf the guards at %turf% are down",
		                        "turf", turf.getDisplayName());
		sendToGang(turf.getOwnerGangId(), turf, line, true);
	}

	@EventHandler
	public void onCaptureProgress(TurfCaptureProgressEvent event) {
		Turf turf = event.getTurf();
		if (event.getProgress() < LAST_STAND) {
			return;
		}
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null) {
			return;
		}
		if (!lastStandAnnounced.add(turf.getId())) {
			return;
		}
		String line = text.text("Alerts.Last_Stand", "&cLast stand at %turf% &7- %progress%%",
		                        "turf", turf.getDisplayName(), "progress", (int) event.getProgress());
		sendToGang(ownerId, turf, line, true);
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		Turf turf = event.getTurf();
		clearContest(turf.getId());
		String turfName = turf.getDisplayName();

		if (event.getOldOwner() != null) {
			String lost = text.text("Alerts.Lost", "&c%turf% lost to %gang%",
			                        "turf", turfName, "gang", GangDisplayNameResolver.resolve(event.getNewOwner()));
			sendToGang(event.getOldOwner().getId(), turf, lost, false);
		}
		String taken = text.text("Alerts.Taken", "&aYou took %turf%", "turf", turfName);
		sendToGang(event.getNewOwner().getId(), turf, taken, false);

		if (!settings.isBroadcastCaptureGlobally()) {
			return;
		}
		String oldName = event.getOldOwner() == null ?
		                 "Unclaimed" :
		                 GangDisplayNameResolver.resolve(event.getOldOwner());
		messages.broadcast("TURF_CAPTURED_BROADCAST",
		                   "turf", turfName,
		                   "gang", GangDisplayNameResolver.resolve(event.getNewOwner()),
		                   "old_gang", oldName);
	}

	@EventHandler
	public void onCaptureFailed(TurfCaptureFailedEvent event) {
		Turf turf = event.getTurf();
		clearContest(turf.getId());
		TurfCaptureFailedEvent.Reason reason = event.getReason();
		if (reason == TurfCaptureFailedEvent.Reason.CANCELLED) {
			return;
		}

		Integer ownerId = turf.getOwnerGangId();
		Integer attackerId = event.getChallengerGangId();
		String turfName = turf.getDisplayName();

		if (reason == TurfCaptureFailedEvent.Reason.STALLED) {
			String stalled = text.text("Alerts.Stalled", "&7%turf%: the attack stalled", "turf", turfName);
			if (ownerId != null) {
				sendToGang(ownerId, turf, stalled, false);
			}
			if (attackerId != null) {
				sendToGang(attackerId, turf, stalled, false);
			}
			return;
		}

		// DEFENDED or ABANDONED
		if (ownerId != null) {
			String held = text.text("Alerts.Held", "&a%turf% held &7against %gang%",
			                        "turf", turfName, "gang", gangName(attackerId));
			sendToGang(ownerId, turf, held, false);
		}
		if (attackerId != null) {
			String failed = text.text("Alerts.Attack_Failed", "&7Your attack on %turf% failed", "turf", turfName);
			sendToGang(attackerId, turf, failed, false);
		}
	}

	private void clearContest(int turfId) {
		halfAnnounced.remove(turfId);
		lastStandAnnounced.remove(turfId);
	}

	private int challengerCount(int turfId, int challengerId) {
		TurfRuntimeState state = turfs.getRuntimeState(turfId);
		return state.getChallengersInside().getOrDefault(challengerId, 0);
	}

	private String gangName(Integer gangId) {
		return GangDisplayNameResolver.resolve(gangId == null ? null : gangs.findById(gangId));
	}

	/**
	 * Compass word from the turf centre to the centroid of the challenger's online members standing inside the turf;
	 * empty when none is found.
	 */
	private String direction(Turf turf, Gang challenger) {
		CuboidRegion region = turf.getRegion();
		int centreX = (region.getMinX() + region.getMaxX()) / 2;
		int centreZ = (region.getMinZ() + region.getMaxZ()) / 2;

		long sumX = 0;
		long sumZ = 0;
		int found = 0;
		for (Player online : Bukkit.getOnlinePlayers()) {
			Integer memberOf = gangIdOf(online);
			if (memberOf == null || memberOf != challenger.getId()) {
				continue;
			}
			Location location = online.getLocation();
			if (!region.contains(location)) {
				continue;
			}
			sumX += location.getBlockX();
			sumZ += location.getBlockZ();
			found++;
		}
		if (found == 0) {
			return "";
		}
		return TurfGeometry.bearing(centreX, centreZ, (int) (sumX / found), (int) (sumZ / found));
	}

	private boolean isAllyOf(int gangId, Gang owner) {
		Gang gang = gangs.findById(gangId);
		return gang != null && gang.isAlly(owner);
	}

	/** Sends the line to every online member of gangId, anywhere. */
	private void sendToGang(int gangId, Turf turf, String line, boolean throttled) {
		for (Player online : Bukkit.getOnlinePlayers()) {
			Integer memberOf = gangIdOf(online);
			if (memberOf == null || memberOf != gangId) {
				continue;
			}
			if (throttled) {
				sendThrottled(online, turf.getId(), line);
			} else {
				online.sendMessage(line);
			}
		}
	}

	private void sendThrottled(Player player, int turfId, String line) {
		String key = player.getUniqueId() + ":" + turfId;
		long now = clock.getAsLong();
		Long last = lastSent.get(key);
		if (last != null && now - last < THROTTLE_MS) {
			return;
		}
		lastSent.put(key, now);
		player.sendMessage(line);
	}

	/** The gang the player belongs to, or null when gangless or unknown. */
	private Integer gangIdOf(Player player) {
		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			return null;
		}
		return user.getGangId();
	}
}
