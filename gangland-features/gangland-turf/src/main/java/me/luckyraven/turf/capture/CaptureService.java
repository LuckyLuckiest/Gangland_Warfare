package me.luckyraven.turf.capture;

import lombok.CustomLog;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.data.TurfRuntimeState;
import me.luckyraven.turf.events.TurfCaptureFailedEvent;
import me.luckyraven.turf.events.TurfCaptureProgressEvent;
import me.luckyraven.turf.events.TurfCaptureStartEvent;
import me.luckyraven.turf.events.TurfCapturedEvent;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.state.TurfState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Drives the capture state machine. Called once per 1-Hz tick by the location tracker, passed the fresh player→turf
 * membership snapshot so it can tally attackers and defenders per turf without re-scanning.
 *
 * <p>Progress model is <b>weighted head-count</b>, not a flat timer:
 * <pre>
 *   net    = attackers - defenders
 *   delta  = net * (100 / captureDurationSeconds)    per 1-Hz tick
 *   progress = clamp(progress + delta, 0, 100)
 * </pre>
 * 2:0 fills in half the time of 1:0; 2:2 pauses; 2:3 rolls progress back toward zero; hitting zero during defender
 * dominance cancels the contest as {@link TurfCaptureFailedEvent.Reason#DEFENDED}.
 *
 * <p>Milestone events fire only on upward crossings — oscillation in a see-saw
 * fight does not re-emit them.
 */
@CustomLog
public final class CaptureService {

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;
	private final CaptureSettings    settings;

	public CaptureService(TurfManager turfs,
	                      GangLookupContract gangs,
	                      UserLookupContract users,
	                      CaptureSettings settings) {
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.users    = users;
		this.settings = settings;
	}

	private static double clamp(double value, double min, double max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Runs one capture tick per turf. Call from the 1-Hz location tracker after player turf membership has been updated
	 * for the tick.
	 */
	public void tick(Map<UUID, Turf> playerTurfCache) {
		long                      now    = System.currentTimeMillis();
		Map<String, List<Player>> inside = indexPlayersByTurf(playerTurfCache);

		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null) {
				continue;
			}

			List<Player> playersInside = inside.getOrDefault(turf.getId(), Collections.emptyList());
			TickGroups   groups        = classify(turf, playersInside);

			switch (state.getState()) {
				case IDLE -> tickIdle(turf, state, groups, now);
				case CONTESTING -> tickContesting(turf, state, groups, now);
				case COOLDOWN -> tickCooldown(turf, state, now);
			}
		}
	}

	public boolean isCapturable(Turf turf, long now) {
		long cooldownMs = settings.getCooldownMinutes() * 60_000L;
		if (now < turf.getLastCaptureTimestamp() + cooldownMs) {
			return false;
		}
		if (turf.isUnclaimed()) {
			return true;
		}
		Gang owner = gangs.findById(turf.getOwnerGangId());
		if (owner == null) {
			return true;
		}
		// Post-logoff grace: protected if any member online OR last-online within the grace window.
		long graceMs = settings.getPostLogoffProtectionMinutes() * 60_000L;
		return now - owner.getLastMemberOnlineAt() > graceMs;
	}

	private Map<String, List<Player>> indexPlayersByTurf(Map<UUID, Turf> cache) {
		Map<String, List<Player>> result = new HashMap<>();
		for (Map.Entry<UUID, Turf> entry : cache.entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			result.computeIfAbsent(entry.getValue().getId(), k -> new ArrayList<>()).add(player);
		}
		return result;
	}

	private TickGroups classify(Turf turf, List<Player> playersInside) {
		TickGroups groups = new TickGroups();
		Integer    owner  = turf.getOwnerGangId();
		for (Player player : playersInside) {
			User<Player> user = users.findByPlayer(player);
			if (user == null || !user.hasGang()) {
				continue;
			}
			int gangId = user.getGangId();
			if (owner != null && gangId == owner) {
				groups.defenders++;
			} else {
				groups.challengersByGang.merge(gangId, 1, Integer::sum);
			}
		}
		return groups;
	}

	private void tickIdle(Turf turf, TurfRuntimeState state, TickGroups groups, long now) {
		if (!isCapturable(turf, now)) {
			return;
		}
		// A single challenger gang with zero defenders starts a contest. Multiple rival
		// challenger gangs cancel each other (bystanders — spec §2.3).
		if (groups.defenders > 0 || groups.challengersByGang.size() != 1) {
			return;
		}
		Map.Entry<Integer, Integer> entry            = groups.challengersByGang.entrySet().iterator().next();
		int                         challengerGangId = entry.getKey();
		Gang                        challenger       = gangs.findById(challengerGangId);
		if (challenger == null) {
			return;
		}

		state.setState(TurfState.CONTESTING);
		state.setChallengerGangId(challengerGangId);
		state.setCaptureProgress(0.0);
		state.setLastChallengerSeenAt(now);

		Bukkit.getPluginManager().callEvent(new TurfCaptureStartEvent(turf, challenger));
	}

	private void tickContesting(Turf turf, TurfRuntimeState state, TickGroups groups, long now) {
		int challengerGangId = state.getChallengerGangId() == null ? -1 : state.getChallengerGangId();
		int challengers      = groups.challengersByGang.getOrDefault(challengerGangId, 0);

		if (challengers > 0) {
			state.setLastChallengerSeenAt(now);
		} else if (now - state.getLastChallengerSeenAt() > settings.getAbandonGraceSeconds() * 1000L) {
			cancel(turf, state, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}

		double base  = 100.0 / Math.max(1, settings.getCaptureDurationSeconds());
		int    net   = challengers - groups.defenders;
		double delta = net * base;

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		// Milestone emission: only on upward crossings, to avoid chat spam in a see-saw fight.
		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				Bukkit.getPluginManager().callEvent(new TurfCaptureProgressEvent(turf, after));
			}
		}

		if (after >= 100.0) {
			complete(turf, state, now);
			return;
		}
		// Defenders ground progress all the way back to zero → contest fails, turf returns to the defender.
		if (after <= 0.0 && challengers == 0) {
			// already handled as ABANDONED above; keep here as safety
			cancel(turf, state, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}
		if (after <= 0.0 && groups.defenders > challengers) {
			cancel(turf, state, TurfCaptureFailedEvent.Reason.DEFENDED);
		}
	}

	private void tickCooldown(Turf turf, TurfRuntimeState state, long now) {
		long cooldownMs = settings.getCooldownMinutes() * 60_000L;
		if (now >= turf.getLastCaptureTimestamp() + cooldownMs) {
			state.setState(TurfState.IDLE);
		}
	}

	private void complete(Turf turf, TurfRuntimeState state, long now) {
		Integer oldOwnerId = turf.getOwnerGangId();
		int     newOwnerId = state.getChallengerGangId();

		Gang oldOwner = oldOwnerId == null ? null : gangs.findById(oldOwnerId);
		Gang newOwner = gangs.findById(newOwnerId);

		turf.setOwnerGangId(newOwnerId);
		turf.setLastCaptureTimestamp(now);
		state.setState(TurfState.COOLDOWN);
		state.setCaptureProgress(0.0);
		state.setChallengerGangId(null);
		turfs.persist(turf);

		if (newOwner != null) {
			Bukkit.getPluginManager().callEvent(new TurfCapturedEvent(turf, oldOwner, newOwner));
		}
	}

	private void cancel(Turf turf, TurfRuntimeState state, TurfCaptureFailedEvent.Reason reason) {
		state.setState(TurfState.IDLE);
		state.setCaptureProgress(0.0);
		state.setChallengerGangId(null);
		state.setLastChallengerSeenAt(0L);
		Bukkit.getPluginManager().callEvent(new TurfCaptureFailedEvent(turf, reason));
	}

	private static final class TickGroups {
		final Map<Integer, Integer> challengersByGang = new HashMap<>();
		int defenders;
	}
}
