package me.luckyraven.turf.capture;

import lombok.CustomLog;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.contract.TurfSoundContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.data.TurfRuntimeState;
import me.luckyraven.turf.events.TurfCaptureFailedEvent;
import me.luckyraven.turf.events.TurfCaptureProgressEvent;
import me.luckyraven.turf.events.TurfCaptureStartEvent;
import me.luckyraven.turf.events.TurfCapturedEvent;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.state.CapturePhase;
import me.luckyraven.turf.state.TurfState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Drives the capture state machine. Called once per 1-Hz tick by the location tracker, passed the fresh player→turf
 * membership snapshot so it can tally attackers and defenders per turf without re-scanning.
 *
 * <p><b>Owned turfs</b> run a single-phase weighted head-count capture:
 * <pre>
 *   net    = attackers - defenders
 *   delta  = net * (100 / captureDurationSeconds)    per 1-Hz tick
 *   progress = clamp(progress + delta, 0, 100)
 * </pre>
 * 2:0 fills in half the time of 1:0; 2:2 pauses; 2:3 rolls progress back toward zero; hitting zero while defenders
 * dominate cancels the contest as {@link TurfCaptureFailedEvent.Reason#DEFENDED}.
 *
 * <p><b>Unclaimed turfs</b> run a two-phase capture. Both phases use the same weighted head-count tug-of-war but
 * with independent durations. The "capturing gang" is whoever has the most members inside; every other gang's members
 * count as opposers in aggregate.
 * <ul>
 *   <li>Phase 1 (CLAIM, 0→100): turf shifts from unclaimed to "potentially the capturing gang's".</li>
 *   <li>Phase 2 (CONSOLIDATE, 0→100): Phase 2 fills and the capture completes.</li>
 *   <li>Phase 2 rolling to 0 reverts to CLAIM at progress=100 with the same capturing gang.</li>
 *   <li>Phase 1 rolling to 0 while a dominant opposing gang is present <i>transfers</i> the contest to that gang at
 *       CONSOLIDATE progress=0 — aggression reward for pushing the original capturer all the way back.</li>
 *   <li>Phase 1 rolling to 0 with no opposing gang → contest cancels (turf becomes genuinely unclaimed).</li>
 * </ul>
 *
 * <p>Milestone events fire only on upward crossings, to avoid chat spam in a see-saw fight.
 */
@CustomLog
public final class CaptureService {

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;
	private final CaptureSettings    settings;
	private final TurfSoundContract  sounds;

	public CaptureService(TurfManager turfs,
	                      GangLookupContract gangs,
	                      UserLookupContract users,
	                      CaptureSettings settings,
	                      TurfSoundContract sounds) {
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.users    = users;
		this.settings = settings;
		this.sounds   = sounds;
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
		long                       now    = System.currentTimeMillis();
		Map<Integer, List<Player>> inside = indexPlayersByTurf(playerTurfCache);

		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null) {
				continue;
			}

			List<Player> playersInside = inside.getOrDefault(turf.getId(), Collections.emptyList());
			TickGroups   groups        = classify(turf, playersInside);

			switch (state.getState()) {
				case IDLE -> tickIdle(turf, state, groups, playersInside, now);
				case CONTESTING -> {
					if (turf.isUnclaimed()) {
						tickContestingUnclaimed(turf, state, groups, playersInside, now);
					} else {
						tickContestingOwned(turf, state, groups, playersInside, now);
					}
				}
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

	private Map<Integer, List<Player>> indexPlayersByTurf(Map<UUID, Turf> cache) {
		Map<Integer, List<Player>> result = new HashMap<>();
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
			// Dead players leave a body at the death location until they click respawn — that body
			// shouldn't count as a live defender or attacker. Skipping them here also naturally pauses
			// a capture the moment the lone attacker goes down, which is the intuitive combat outcome.
			if (player.isDead()) {
				continue;
			}
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

	private void tickIdle(Turf turf, TurfRuntimeState state, TickGroups groups, List<Player> playersInside, long now) {
		if (!isCapturable(turf, now)) {
			return;
		}
		if (turf.isUnclaimed()) {
			// Unclaimed: pick the gang with the most members. Ties at the top → no contest starts
			// (there's no single "leader"). A lone gang always qualifies.
			Integer leader = dominantGang(groups.challengersByGang, null);
			if (leader == null) {
				return;
			}
			Gang challenger = gangs.findById(leader);
			if (challenger == null) {
				return;
			}
			startContest(turf, state, challenger, CapturePhase.CLAIM, 0.0, playersInside, now);
			return;
		}
		// Owned turf: classic rule — exactly one challenger gang, zero defenders. Multiple rival
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
		startContest(turf, state, challenger, CapturePhase.CLAIM, 0.0, playersInside, now);
	}

	private void tickContestingOwned(Turf turf, TurfRuntimeState state, TickGroups groups,
	                                 List<Player> playersInside, long now) {
		int challengerGangId = state.getChallengerGangId() == null ? -1 : state.getChallengerGangId();
		int challengers      = groups.challengersByGang.getOrDefault(challengerGangId, 0);

		if (challengers > 0) {
			state.setLastChallengerSeenAt(now);
		} else if (now - state.getLastChallengerSeenAt() > settings.getAbandonGraceSeconds() * 1000L) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}

		double base  = 100.0 / Math.max(1, settings.getCaptureDurationSeconds());
		int    net   = challengers - groups.defenders;
		double delta = net * base;

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				Bukkit.getPluginManager().callEvent(new TurfCaptureProgressEvent(turf, after));
			}
		}

		if (after >= 100.0) {
			complete(turf, state, playersInside, now);
			return;
		}
		if (after <= 0.0 && challengers == 0) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}
		if (after <= 0.0 && groups.defenders > challengers) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.DEFENDED);
		}
	}

	private void tickContestingUnclaimed(Turf turf, TurfRuntimeState state, TickGroups groups,
	                                     List<Player> playersInside, long now) {
		int capturingGangId = state.getChallengerGangId() == null ? -1 : state.getChallengerGangId();
		int capturing       = groups.challengersByGang.getOrDefault(capturingGangId, 0);

		// Sum every other gang's members as combined opposers — the tug-of-war is 1-vs-rest.
		int opposers = 0;
		for (Map.Entry<Integer, Integer> entry : groups.challengersByGang.entrySet()) {
			if (entry.getKey() != capturingGangId) {
				opposers += entry.getValue();
			}
		}

		if (capturing > 0) {
			state.setLastChallengerSeenAt(now);
		} else if (opposers == 0
		           && now - state.getLastChallengerSeenAt() > settings.getAbandonGraceSeconds() * 1000L) {
			// Capturing gang left AND no opposers to transfer to → abandon.
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}

		int phaseDurationSeconds = state.getPhase() == CapturePhase.CLAIM
		                           ? settings.getUnclaimedPhase1Seconds()
		                           : settings.getUnclaimedPhase2Seconds();
		double base  = 100.0 / Math.max(1, phaseDurationSeconds);
		int    net   = capturing - opposers;
		double delta = net * base;

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				Bukkit.getPluginManager().callEvent(new TurfCaptureProgressEvent(turf, after));
			}
		}

		// Phase-boundary transitions ------------------------------------------------------------
		if (state.getPhase() == CapturePhase.CLAIM && after >= 100.0) {
			// Phase 1 done → Phase 2 starts at zero, same gang, same bar.
			state.setPhase(CapturePhase.CONSOLIDATE);
			state.setCaptureProgress(0.0);
			return;
		}
		if (state.getPhase() == CapturePhase.CONSOLIDATE && after >= 100.0) {
			complete(turf, state, playersInside, now);
			return;
		}
		if (state.getPhase() == CapturePhase.CONSOLIDATE && after <= 0.0) {
			// Phase 2 rolled back → revert to Phase 1 at the boundary (100). Same capturing gang —
			// they still have a claim on the turf; Phase 2 has to be redone but Phase 1 is intact.
			state.setPhase(CapturePhase.CLAIM);
			state.setCaptureProgress(100.0);
			return;
		}
		if (state.getPhase() == CapturePhase.CLAIM && after <= 0.0) {
			// Phase 1 rolled back to zero. If a dominant opposer is present, transfer the contest
			// to them starting at Phase 2 progress=0 (they've earned the skip by wrestling the
			// previous gang all the way back). If no opposer, just cancel — turf goes unclaimed.
			Integer takeoverId = dominantGang(groups.challengersByGang, capturingGangId);
			if (takeoverId == null) {
				cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
				return;
			}
			Gang takeover = gangs.findById(takeoverId);
			if (takeover == null) {
				cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
				return;
			}
			// Tear down the previous bars/state cleanly, then start the new gang's contest so every
			// listener (bossbar, notifier, status) sees the transition as a proper restart.
			Bukkit.getPluginManager()
			      .callEvent(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED));
			startContest(turf, state, takeover, CapturePhase.CONSOLIDATE, 0.0, playersInside, now);
		}
	}

	private void tickCooldown(Turf turf, TurfRuntimeState state, long now) {
		long cooldownMs = settings.getCooldownMinutes() * 60_000L;
		if (now >= turf.getLastCaptureTimestamp() + cooldownMs) {
			state.setState(TurfState.IDLE);
		}
	}

	private void startContest(Turf turf, TurfRuntimeState state, Gang challenger, CapturePhase phase, double progress,
	                          List<Player> playersInside, long now) {
		state.setState(TurfState.CONTESTING);
		state.setPhase(phase);
		state.setChallengerGangId(challenger.getId());
		state.setCaptureProgress(progress);
		state.setLastChallengerSeenAt(now);

		for (Player player : playersInside) {
			sounds.playCaptureStart(player);
		}
		Bukkit.getPluginManager().callEvent(new TurfCaptureStartEvent(turf, challenger));
	}

	private void complete(Turf turf, TurfRuntimeState state, List<Player> playersInside, long now) {
		Integer oldOwnerId = turf.getOwnerGangId();
		int     newOwnerId = state.getChallengerGangId();

		Gang oldOwner = oldOwnerId == null ? null : gangs.findById(oldOwnerId);
		Gang newOwner = gangs.findById(newOwnerId);

		turf.setOwnerGangId(newOwnerId);
		turf.setLastCaptureTimestamp(now);
		state.setState(TurfState.COOLDOWN);
		state.setPhase(CapturePhase.CLAIM);
		state.setCaptureProgress(0.0);
		state.setChallengerGangId(null);
		turfs.persist(turf);

		for (Player player : playersInside) {
			sounds.playCaptureComplete(player);
		}
		if (newOwner != null) {
			Bukkit.getPluginManager().callEvent(new TurfCapturedEvent(turf, oldOwner, newOwner));
		}
	}

	private void cancel(Turf turf, TurfRuntimeState state, List<Player> playersInside,
	                    TurfCaptureFailedEvent.Reason reason) {
		state.reset();
		for (Player player : playersInside) {
			sounds.playCaptureFailed(player);
		}
		Bukkit.getPluginManager().callEvent(new TurfCaptureFailedEvent(turf, reason));
	}

	/**
	 * Returns the gang id with strictly the most members in {@code counts}, optionally excluding one gang id (used when
	 * picking a replacement capturer after the current one has been pushed back to zero). Returns {@code null} on ties
	 * at the top or on an empty map.
	 */
	private Integer dominantGang(Map<Integer, Integer> counts, Integer exclude) {
		Integer best       = null;
		int     bestCount  = 0;
		int     tiedAtBest = 0;
		for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
			if (exclude != null && entry.getKey().intValue() == exclude.intValue()) {
				continue;
			}
			int value = entry.getValue();
			if (value > bestCount) {
				best       = entry.getKey();
				bestCount  = value;
				tiedAtBest = 1;
			} else if (value == bestCount) {
				tiedAtBest++;
			}
		}
		return tiedAtBest == 1 ? best : null;
	}

	private static final class TickGroups {
		final Map<Integer, Integer> challengersByGang = new HashMap<>();
		int defenders;
	}
}
