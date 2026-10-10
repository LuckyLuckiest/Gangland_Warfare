package org.luckyraven.gangland.turf.capture;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
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
 * <p>One capture engine for every turf (0.17.0, D5). A turf starts a contest when exactly one gang has strictly the
 * most counted attackers and, on an owned turf, more than the counted owner and ally players inside. Ties never start;
 * guards never block a start. The contest runs one tug-of-war at {@code Capture.Duration_Seconds}:
 * <pre>
 *   net      = attackers - defenders        (owned: defenders = owner + ally players + counted guards)
 *   delta    = net * (100 / captureDurationSeconds)    per 1-Hz tick, at most 100 / Min_Capture_Seconds
 *   progress = clamp(progress + delta, 0, 100)
 * </pre>
 * An attacker counts after {@code Presence_Seconds} inside; defenders count at once. While a contest runs, a player who
 * joined his gang, or an ally who allied, after it started is a bystander until it ends. An empty contest freezes its
 * bar and ends {@link TurfCaptureFailedEvent.Reason#ABANDONED} once {@code Abandon_Grace_Seconds} pass without a counted
 * attacker. A contest running {@code Max_Contest_Minutes} ends {@link TurfCaptureFailedEvent.Reason#STALLED}. Reaching
 * 100 completes the capture unless the attacking gang is now at its turf cap ({@code CANCELLED}); reaching 0 while the
 * defenders lead ends {@link TurfCaptureFailedEvent.Reason#DEFENDED}.
 *
 * <p>Milestone events fire only on upward crossings, to avoid chat spam in a see-saw fight.
 */
@CustomLog
public final class CaptureService {

	/** Relation of a player to the turf on the last tick: the owner's own gang, an ally of the owner, or any other gang. */
	private static final int OWN   = 0;
	private static final int ALLY  = 1;
	private static final int RIVAL = 2;

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;
	private final CaptureSettings    settings;
	private final TurfSoundContract  sounds;
	private final TurfOwnership      ownership;
	private final CaptureEligibility     eligibility;
	private final Supplier<CaptureRules> rules;
	private final IntUnaryOperator       guardsAlive;

	/** Stamp of when a player entered the turf in his current relation; restarts on leaving, dying or a relation change. */
	private record Entry(int turfId, int gangId, int relation, long enteredAt) {
	}

	private final Map<UUID, Entry> entries = new HashMap<>();

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
		entries.keySet().removeIf(uuid -> !playerTurfCache.containsKey(uuid));
		Map<Integer, List<Player>> inside = indexPlayersByTurf(playerTurfCache);

		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null) {
				continue;
			}

			List<Player> playersInside = inside.getOrDefault(turf.getId(), Collections.emptyList());
			TickGroups   groups        = classify(turf, state, playersInside, now);
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
				case CONTESTING -> tickContesting(turf, state, groups, playersInside, now);
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

	/**
	 * Counts the players inside one turf. Only counted values are kept: an attacker counts after Presence_Seconds inside,
	 * a defender at once, and during a contest a player who arrived after it started is left out (A4).
	 */
	private TickGroups classify(Turf turf, TurfRuntimeState state, List<Player> playersInside, long now) {
		TickGroups groups       = new TickGroups();
		Integer    owner        = turf.getOwnerGangId();
		Gang       ownerGang    = owner == null ? null : gangs.findById(owner);
		long       contestStart = state.getState() == TurfState.CONTESTING ? state.getContestStartedAt() : 0L;
		long       presenceMs   = rules.get().presenceSeconds() * 1000L;
		for (Player player : playersInside) {
			UUID uuid = player.getUniqueId();
			// Dead players leave a body at the death location until they click respawn - that body shouldn't count as a
			// live defender or attacker, and dying restarts the presence count.
			if (player.isDead()) {
				entries.remove(uuid);
				continue;
			}
			User<Player> user = users.findByPlayer(player);
			if (user == null || !user.hasGang()) {
				continue;
			}
			int gangId   = user.getGangId();
			int relation = relationOf(owner, ownerGang, gangId);
			Entry entry  = entries.get(uuid);
			if (entry == null || entry.turfId() != turf.getId() || entry.gangId() != gangId
			    || entry.relation() != relation) {
				entry = new Entry(turf.getId(), gangId, relation, now);
				entries.put(uuid, entry);
			}
			if (contestStart > 0 && arrivedAfter(gangId, uuid, relation, owner, contestStart)) {
				continue;
			}
			if (relation == RIVAL) {
				if (now - entry.enteredAt() >= presenceMs) {
					groups.challengersByGang.merge(gangId, 1, Integer::sum);
				}
			} else {
				groups.defenders++;
			}
		}
		return groups;
	}

	private int relationOf(Integer owner, Gang ownerGang, int gangId) {
		if (owner == null) {
			return RIVAL;
		}
		if (gangId == owner) {
			return OWN;
		}
		return isOwnerAlly(ownerGang, gangId) ? ALLY : RIVAL;
	}

	/** A4: a player may not count on his side because he joined, or his alliance with the owner formed, after the start. */
	private boolean arrivedAfter(int gangId, UUID uuid, int relation, Integer owner, long contestStart) {
		if (memberJoinedAfter(gangId, uuid, contestStart)) {
			return true;
		}
		return relation == ALLY && allianceFormedAfter(owner, gangId, contestStart);
	}

	/** True when the member record exists and says he joined after the contest started; no record counts. */
	private boolean memberJoinedAfter(int gangId, UUID uuid, long contestStart) {
		Gang gang = gangs.findById(gangId);
		if (gang == null) {
			return false;
		}
		for (Member member : gang.getMembers()) {
			if (member.getUuid().equals(uuid)) {
				return member.getGangJoinDateLong() > contestStart;
			}
		}
		return false;
	}

	/** True when an alliance record between the owner and the ally exists and was formed after the start; none counts. */
	private boolean allianceFormedAfter(Integer owner, int allyGangId, long contestStart) {
		long    since     = Long.MAX_VALUE;
		boolean found     = false;
		Gang    ownerGang = gangs.findById(owner);
		if (ownerGang != null) {
			for (GangAlliance alliance : ownerGang.getAllies()) {
				if (alliance.ally().getId() == allyGangId) {
					since = Math.min(since, alliance.since());
					found = true;
				}
			}
		}
		Gang allyGang = gangs.findById(allyGangId);
		if (allyGang != null) {
			for (GangAlliance alliance : allyGang.getAllies()) {
				if (alliance.ally().getId() == owner) {
					since = Math.min(since, alliance.since());
					found = true;
				}
			}
		}
		return found && since > contestStart;
	}

	private boolean isOwnerAlly(Gang ownerGang, int otherGangId) {
		if (ownerGang == null) {
			return false;
		}
		Gang other = gangs.findById(otherGangId);
		return other != null && other.isAlly(ownerGang);
	}

	private void tickIdle(Turf turf, TurfRuntimeState state, TickGroups groups, List<Player> playersInside, long now) {
		// Plurality (TF-48): one gang with strictly the most counted attackers, and more than the counted defenders
		// (always 0 on an unclaimed turf). Ties never start; guards are not counted while IDLE.
		Integer challengerId = dominantGang(groups.challengersByGang);
		if (challengerId == null || groups.challengersByGang.get(challengerId) <= groups.defenders) {
			return;
		}
		if (!eligibility.evaluateFor(turf, challengerId, now).attackable()) {
			return;
		}
		Gang challenger = gangs.findById(challengerId);
		if (challenger == null) {
			return;
		}
		startContest(turf, state, challenger, playersInside, now);
	}

	/**
	 * One bar (D5) for owned and unclaimed turfs: the stall, the abandon grace, the freeze, the rate clamp, the milestones
	 * and the completion or the cancel. Guards count as defenders on an owned turf only (D4).
	 */
	private void tickContesting(Turf turf, TurfRuntimeState state, TickGroups groups, List<Player> playersInside,
	                            long now) {
		CaptureRules current      = rules.get();
		int          challengerId = state.getChallengerGangId() == null ? -1 : state.getChallengerGangId();
		int          attackers    = groups.challengersByGang.getOrDefault(challengerId, 0);

		if (current.maxContestMinutes() > 0 && state.getContestStartedAt() > 0
		    && now - state.getContestStartedAt() >= current.maxContestMinutes() * 60_000L) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.STALLED);
			return;
		}

		boolean owned  = !turf.isUnclaimed();
		int     guards = owned ? Math.min(guardsAlive.applyAsInt(turf.getId()), current.guardWeightCap()) : 0;
		state.setGuardsCounted(guards);
		int defenders = owned ? groups.defenders + guards : othersInside(groups, challengerId);

		if (attackers > 0) {
			state.setLastChallengerSeenAt(now);
		} else if (now - state.getLastChallengerSeenAt() > settings.getAbandonGraceSeconds() * 1000L) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.ABANDONED);
			return;
		}
		if (attackers == 0 && defenders == 0) {
			// TF-12: nobody counted on either side, so the bar holds until the grace window ends the contest.
			return;
		}

		int    net   = attackers - defenders;
		double delta = net * 100.0 / Math.max(1, settings.getCaptureDurationSeconds());
		if (current.minCaptureSeconds() > 0) {
			delta = Math.min(delta, 100.0 / current.minCaptureSeconds());
		}

		double before = state.getCaptureProgress();
		double after  = clamp(before + delta, 0.0, 100.0);
		state.setCaptureProgress(after);

		for (int milestone : settings.getProgressMilestones()) {
			if (before < milestone && after >= milestone) {
				Bukkit.getPluginManager()
				      .callEvent(new TurfCaptureProgressEvent(turf, after, CapturePhase.CLAIM, after, 0.0));
			}
		}

		if (after >= 100.0) {
			// P3.5: a gang that filled its turf cap during the contest does not get the turf.
			if (eligibility.overCap(challengerId)) {
				cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.CANCELLED);
				return;
			}
			complete(turf, state, playersInside, now);
			return;
		}
		// Only a bar pushed down to 0 is DEFENDED. A guard-held bar that never moved (before == 0) stays CONTESTING,
		// so guards that are deployed at start cannot end their own contest on the first tick.
		if (after <= 0.0 && before > 0.0 && defenders > attackers) {
			cancel(turf, state, playersInside, TurfCaptureFailedEvent.Reason.DEFENDED);
		}
	}

	/** Unclaimed turf: every counted member of a gang other than the challenger is opposition. */
	private int othersInside(TickGroups groups, int challengerId) {
		int others = 0;
		for (Map.Entry<Integer, Integer> entry : groups.challengersByGang.entrySet()) {
			if (entry.getKey() != challengerId) {
				others += entry.getValue();
			}
		}
		return others;
	}

	private void tickCooldown(Turf turf, TurfRuntimeState state, long now) {
		long cooldownMs = settings.getCooldownMinutes() * 60_000L;
		if (now >= turf.getLastCaptureTimestamp() + cooldownMs) {
			state.setState(TurfState.IDLE);
		}
	}

	private void startContest(Turf turf, TurfRuntimeState state, Gang challenger, List<Player> playersInside,
	                          long now) {
		state.setState(TurfState.CONTESTING);
		state.setPhase(CapturePhase.CLAIM);
		state.setChallengerGangId(challenger.getId());
		state.setCaptureProgress(0.0);
		state.setLastChallengerSeenAt(now);
		state.setContestStartedAt(now);
		state.setGuardsCounted(0);

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
		state.setContestStartedAt(0L);
		state.setGuardsCounted(0);
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
	 * Returns the gang id with strictly the most members in {@code counts}. Returns {@code null} on ties at the top or on
	 * an empty map.
	 */
	private Integer dominantGang(Map<Integer, Integer> counts) {
		Integer best       = null;
		int     bestCount  = 0;
		int     tiedAtBest = 0;
		for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
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
