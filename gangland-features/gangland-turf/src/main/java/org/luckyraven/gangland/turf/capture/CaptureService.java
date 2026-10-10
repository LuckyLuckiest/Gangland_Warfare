package org.luckyraven.gangland.turf.capture;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureProgressEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

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
	private final TurfOwnership      ownership;
	private final CaptureEligibility     eligibility;
	private final Supplier<CaptureRules> rules;
	private final IntUnaryOperator       guardsAlive;

	/** Today's rules (CaptureRules.LEGACY), no guards - for tests written before W2. */
	public CaptureService(TurfManager turfs,
	                      GangLookupContract gangs,
	                      UserLookupContract users,
	                      CaptureSettings settings,
	                      TurfSoundContract sounds,
	                      TurfOwnership ownership) {
		this(turfs, gangs, users, settings, sounds, ownership, new CaptureEligibility(gangs, settings),
		     () -> CaptureRules.LEGACY, turfId -> 0);
	}

	/**
	 * @param eligibility the shared capture verdict (its own bean since 0.17.0)
	 * @param rules       TurfRules::capture in production
	 * @param guardsAlive turf id -> living guards of that turf (TurfDefenderDeployer::aliveCount)
	 */
	public CaptureService(TurfManager turfs,
	                      GangLookupContract gangs,
	                      UserLookupContract users,
	                      CaptureSettings settings,
	                      TurfSoundContract sounds,
	                      TurfOwnership ownership,
	                      CaptureEligibility eligibility,
	                      Supplier<CaptureRules> rules,
	                      IntUnaryOperator guardsAlive) {
		this.turfs       = turfs;
		this.gangs       = gangs;
		this.users       = users;
		this.settings    = settings;
		this.sounds      = sounds;
		this.ownership   = ownership;
		this.eligibility = eligibility;
		this.rules       = rules;
		this.guardsAlive = guardsAlive;
	}

	/** The capture verdict every surface reads (map, HUD, info card, boss bar). */
	public CaptureEligibility eligibility() {
		return eligibility;
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
		tick(playerTurfCache, System.currentTimeMillis());
	}

	/** Tests pass a fixed clock; production calls the one-argument form. */
	void tick(Map<UUID, Turf> playerTurfCache, long now) {
		Map<Integer, List<Player>> inside = indexPlayersByTurf(playerTurfCache);

		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null) {
				continue;
			}

			List<Player> playersInside = inside.getOrDefault(turf.getId(), Collections.emptyList());
			TickGroups   groups        = classify(turf, playersInside);
			state.setChallengersInside(groups.challengersByGang.isEmpty() ? Map.of()
			                                                              : Map.copyOf(groups.challengersByGang));
			state.setDefendersInside(groups.defenders);
			// TF-27: runtime state is not persisted, so a turf captured just before a restart loads as IDLE. Its cooldown is
			// still enforced (isCapturable reads the persisted timestamp); this only makes the displayed state say so.
			if (state.getState() == TurfState.IDLE
			    && now < turf.getLastCaptureTimestamp() + settings.getCooldownMinutes() * 60_000L) {
				state.setState(TurfState.COOLDOWN);
			}

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
		return eligibility.evaluate(turf, now).attackable();
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
		TickGroups groups    = new TickGroups();
		Integer    owner     = turf.getOwnerGangId();
		Gang       ownerGang = owner == null ? null : gangs.findById(owner);
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
			// Allies of the owner are folded into the defender bucket so they can't trigger or progress a
			// contest against an ally's turf, and the Quartermaster/defenders never name the ally's gang
			// as challenger (which would target every ally member).
			if (owner != null && (gangId == owner || isOwnerAlly(ownerGang, gangId))) {
				groups.defenders++;
			} else {
				groups.challengersByGang.merge(gangId, 1, Integer::sum);
			}
		}
		return groups;
	}

	private boolean isOwnerAlly(Gang ownerGang, int otherGangId) {
		if (ownerGang == null) {
			return false;
		}
		Gang other = gangs.findById(otherGangId);
		return other != null && other.isAlly(ownerGang);
	}

	private void tickIdle(Turf turf, TurfRuntimeState state, TickGroups groups, List<Player> playersInside, long now) {
		if (!isCapturable(turf, now)) {
			return;
		}
		if (turf.isUnclaimed()) {
			// Phase 1 is global — any gang member inside is enough to start the contest. The "challenger"
			// chosen here is mostly cosmetic (used as the initial bossbar label until Phase 1 completes
			// and a fresh dominant gang is elected for Phase 2). Pick the dominant gang if there is one,
			// otherwise the lowest gang id for determinism. Without this, two evenly-matched gangs
			// arriving simultaneously would never trigger a contest.
			if (groups.challengersByGang.isEmpty()) {
				return;
			}
			Integer initialChallengerId = dominantGang(groups.challengersByGang, null);
			if (initialChallengerId == null) {
				int lowest = Integer.MAX_VALUE;
				for (Integer gangId : groups.challengersByGang.keySet()) {
					if (gangId < lowest) {
						lowest = gangId;
					}
				}
				initialChallengerId = lowest;
			}
			Gang challenger = gangs.findById(initialChallengerId);
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
		}

		double base = 100.0 / Math.max(1, settings.getCaptureDurationSeconds());
		// Empty contest decays at 1v0 rate so the bar visibly winds down (and unclaimed turfs roll their
		// phase back through CLAIM) instead of freezing for the abandon-grace window. The cancel branch
		// at progress<=0 still ends the contest cleanly, but only after the player gets visual feedback.
		int    net   = (challengers == 0 && groups.defenders == 0) ? -1 : challengers - groups.defenders;
		double delta = net * base;

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				// Owned-turf captures are single-phase; the dual-bar UI just renders bar B and ignores the
				// claim split, so we report (CLAIM, after, 0) — the legacy "progress" stays meaningful.
				Bukkit.getPluginManager()
				      .callEvent(new TurfCaptureProgressEvent(turf, after, CapturePhase.CLAIM, after, 0.0));
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
		int totalInside = 0;
		for (Integer count : groups.challengersByGang.values()) {
			totalInside += count;
		}

		// Abandon grace applies globally in both phases — "no gang member has been inside for X seconds"
		// ends the contest. Phase 1 doesn't decrement on its own (see below) so we need the grace timer
		// to stop a half-filled Phase 1 from lingering forever on an empty turf.
		if (totalInside > 0) {
			state.setLastChallengerSeenAt(now);
		} else if (now - state.getLastChallengerSeenAt() > settings.getAbandonGraceSeconds() * 1000L) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}

		int phaseDurationSeconds = state.getPhase() == CapturePhase.CLAIM
		                           ? settings.getUnclaimedPhase1Seconds()
		                           : settings.getUnclaimedPhase2Seconds();
		double base = 100.0 / Math.max(1, phaseDurationSeconds);

		double delta;
		if (state.getPhase() == CapturePhase.CLAIM) {
			// Phase 1 is GLOBAL — anyone inside (regardless of gang) contributes. Rivals who enter
			// to "stop" a capturing gang are actually forced to help complete Phase 1 alongside them;
			// the fight over who owns the turf happens in Phase 2. Empty Phase 1 freezes rather than
			// decaying so a partially-filled claim survives a brief gap before grace ends it.
			delta = totalInside > 0 ? base : 0.0;
		} else {
			// Phase 2 is the contestable phase: per-gang tug-of-war with decay-on-empty so the bar
			// visibly winds back down to zero (rolling into Phase 1 @100) instead of freezing.
			int capturingGangId = state.getChallengerGangId() == null ? -1 : state.getChallengerGangId();
			int capturing       = groups.challengersByGang.getOrDefault(capturingGangId, 0);
			int opposers        = 0;
			for (Map.Entry<Integer, Integer> entry : groups.challengersByGang.entrySet()) {
				if (entry.getKey() != capturingGangId) {
					opposers += entry.getValue();
				}
			}
			int net = (capturing == 0 && opposers == 0) ? -1 : capturing - opposers;
			delta = net * base;
		}

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				// Split the two halves so the dual-bar UI can render claim + consolidate as separate bars
				// without re-reading TurfRuntimeState off-thread. CLAIM phase pins consolidate at 0;
				// CONSOLIDATE phase pins claim at 100 (it already filled).
				double claim       = state.getPhase() == CapturePhase.CLAIM ? after : 100.0;
				double consolidate = state.getPhase() == CapturePhase.CONSOLIDATE ? after : 0.0;
				Bukkit.getPluginManager()
				      .callEvent(new TurfCaptureProgressEvent(turf, after, state.getPhase(), claim, consolidate));
			}
		}

		// Phase-boundary transitions ------------------------------------------------------------
		if (state.getPhase() == CapturePhase.CLAIM && after >= 100.0) {
			// Phase 1 done → pick the gang that's currently dominant inside. Ties stall at 100 until
			// someone gains dominance; empty rooms also stall. The winning gang becomes the challenger
			// for Phase 2, re-firing TurfCaptureStartEvent so listeners pick up the new bossbar colour
			// even if a prior gang had previously held the contest.
			Integer dominant = dominantGang(groups.challengersByGang, null);
			if (dominant == null) {
				return;
			}
			Gang newOwner = gangs.findById(dominant);
			if (newOwner == null) {
				return;
			}
			state.setPhase(CapturePhase.CONSOLIDATE);
			state.setCaptureProgress(0.0);
			state.setChallengerGangId(dominant);
			for (Player player : playersInside) {
				sounds.playCaptureStart(player);
			}
			Bukkit.getPluginManager().callEvent(new TurfCaptureStartEvent(turf, newOwner));
			return;
		}
		if (state.getPhase() == CapturePhase.CONSOLIDATE && after >= 100.0) {
			complete(turf, state, playersInside, now);
			return;
		}
		if (state.getPhase() == CapturePhase.CONSOLIDATE && after <= 0.0) {
			// Phase 2 rolled back → revert to Phase 1 at the boundary (100). Challenger is cleared so the
			// next Phase 1 completion picks a fresh dominant gang — this is the ping-pong cycle: rivals
			// who push the current Phase 2 owner back to 0 get a fair shot at claiming Phase 2 for
			// themselves on the next transition.
			state.setPhase(CapturePhase.CLAIM);
			state.setCaptureProgress(100.0);
			state.setChallengerGangId(null);
			return;
		}
		if (state.getPhase() == CapturePhase.CLAIM && after <= 0.0) {
			// With Phase 1 non-decrementing this branch is unreachable during normal ticks; kept as a
			// defensive cancel in case future tuning reintroduces decay.
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
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
		Integer newOwnerId = state.getChallengerGangId();
		Gang    newOwner   = newOwnerId == null ? null : gangs.findById(newOwnerId);
		if (newOwner == null) {
			// The challenger disbanded mid-contest: cancel instead of writing a dangling owner id (TF-01).
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.CANCELLED);
			return;
		}

		Integer oldOwnerId = turf.getOwnerGangId();
		Gang    oldOwner   = oldOwnerId == null ? null : gangs.findById(oldOwnerId);

		turf.setLastCaptureTimestamp(now);
		state.setState(TurfState.COOLDOWN);
		state.setPhase(CapturePhase.CLAIM);
		state.setCaptureProgress(0.0);
		state.setChallengerGangId(null);
		boolean changed = ownership.setOwner(turf, newOwnerId, TurfOwnerChangedEvent.Cause.CAPTURE);
		if (!changed) {
			turfs.persist(turf);
		}

		for (Player player : playersInside) {
			sounds.playCaptureComplete(player);
		}
		Bukkit.getPluginManager().callEvent(new TurfCapturedEvent(turf, oldOwner, newOwner));
	}

	private void cancel(Turf turf, TurfRuntimeState state, List<Player> playersInside,
	                    TurfCaptureFailedEvent.Reason reason) {
		Integer challenger = state.getChallengerGangId();
		state.reset();
		for (Player player : playersInside) {
			sounds.playCaptureFailed(player);
		}
		Bukkit.getPluginManager().callEvent(new TurfCaptureFailedEvent(turf, reason, challenger));
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
