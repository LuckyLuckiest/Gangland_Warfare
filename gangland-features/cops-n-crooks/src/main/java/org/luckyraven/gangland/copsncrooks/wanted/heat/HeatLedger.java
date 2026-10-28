package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The one ledger every crime feeds: each {@link CrimeCommittedEvent} adds weighted heat to the player's chase, and heat
 * crossing a star threshold calls the core star handler until the stars match. Main thread only.
 *
 * @since 0.15.0
 */
public final class HeatLedger {

	private final ChaseConfigLoader      config;
	private final UserManager<Player>    users;
	private final CrimeService           crimes;
	private final Predicate<Player>      copSight;
	private final Predicate<Location>    contestedTurf;
	private final IntSupplier            streakWindowSeconds;
	private final LongSupplier           clock;

	private final Map<UUID, Chase>             chases   = new ConcurrentHashMap<>();
	private final Map<UUID, Map<UUID, Long>>   assaults = new ConcurrentHashMap<>();

	private volatile Consumer<Player> starTrigger;
	/** The criminal whose late report is landing ({@link #commitReported}), and who reported it; main thread only. */
	private @Nullable UUID            reporting;
	private @Nullable ReportKind      reportingKind;
	/** Where the crime landing under {@link #commitReported} was done; main thread only (0.21.2, T-543). */
	private @Nullable Location        reportingAt;
	/** The cop hit by the Assault_Cop / Kill_Cop being committed right now; main thread only (0.21.2). */
	private @Nullable UUID            booking;

	public HeatLedger(ChaseConfigLoader config, UserManager<Player> users, CrimeService crimes,
	                  Predicate<Player> copSight, Predicate<Location> contestedTurf,
	                  IntSupplier streakWindowSeconds, LongSupplier clock) {
		this.config              = config;
		this.users               = users;
		this.crimes              = crimes;
		this.copSight            = copSight;
		this.contestedTurf       = contestedTurf;
		this.streakWindowSeconds = streakWindowSeconds;
		this.clock               = clock;
	}

	/** The turf at {@code at} when it is being captured right now, else null; null-safe. */
	public static @Nullable Turf contestedTurfAt(@Nullable TurfManager turfs, @Nullable Location at) {
		if (turfs == null || at == null) return null;

		Turf turf = turfs.findAt(at);
		if (turf == null) return null;

		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		return state != null && state.getState() == TurfState.CONTESTING ? turf : null;
	}

	/** The core star handler, replayed until the stars match the heat. */
	public void setStarTrigger(@Nullable Consumer<Player> trigger) {
		this.starTrigger = trigger;
	}

	/**
	 * Runs the commit of a report that landed late: the crime it records for {@code criminal} carries {@code kind}, so
	 * the star card's chat line names who reported it (0.21.1 Q7, 0.21.2 S3). Nothing else does: a safe alarm, a store
	 * hold-up or a crew mate's share commits with a witness too, but nobody reported it.
	 */
	public boolean commitReported(Player criminal, ReportKind kind, BooleanSupplier commit) {
		reporting     = criminal.getUniqueId();
		reportingKind = kind;
		try {
			return commit.getAsBoolean();
		} finally {
			reporting     = null;
			reportingKind = null;
			reportingAt   = null;
		}
	}

	/**
	 * The spot of the crime a late report is landing for {@code playerId} right now ({@link #commitReported}), else
	 * null: the wanted start it causes names and tips that spot, not where he stands now (0.21.2, T-543).
	 */
	public @Nullable Location reportedScene(UUID playerId) {
		return playerId.equals(reporting) ? reportingAt : null;
	}

	/** The cop the Assault_Cop or Kill_Cop being committed right now hit (the witness gate's victim), else null. */
	public @Nullable UUID currentVictim() {
		return booking;
	}

	/** @return the heat added, 0 when the crime is ignored */
	public double record(CrimeCommittedEvent event) {
		HeatSettings heat = config.get().heat();
		if (!heat.enabled()) return 0;

		Player       player = event.getPlayer();
		User<Player> user   = users.getUser(player);
		if (user == null) return 0;

		int weight = heat.weightOf(event.getCrimeId());
		if (weight <= 0) return 0;

		Wanted wanted   = user.getWanted();
		int    maxLevel = wanted.getMaxLevel();
		long   now      = clock.getAsLong();
		Chase  chase    = chases.computeIfAbsent(player.getUniqueId(),
		                                         id -> new Chase(heat.floorOf(wanted.getLevel(), maxLevel)));

		double mult   = 1;
		int    window = streakWindowSeconds.getAsInt();
		if (window > 0 && !chase.crimes.isEmpty()
		    && now - chase.crimes.get(chase.crimes.size() - 1).at() <= window * 1000L) {
			mult *= heat.streakBonus();
		}
		mult *= witnessFactor(event, heat);
		if (Crimes.KILL_PLAYER.equals(event.getCrimeId()) && contestedTurf.test(event.getLocation())) {
			mult *= heat.turfWarMultiplier();
		}

		double added = weight * mult;
		chase.heat += added;
		ReportKind kind = player.getUniqueId().equals(reporting) ? reportingKind : null;
		if (kind != null) reportingAt = event.getLocation();
		chase.crimes.add(new CrimeRecord(event.getCrimeId(), added, now, event.getLocation(), kind,
		                                 event.getWitnesses()));

		int target = heat.starsFor(chase.heat, maxLevel);
		Consumer<Player> trigger = starTrigger;
		for (int i = 0; trigger != null && i < maxLevel && wanted.getLevel() < target; i++) {
			int before = wanted.getLevel();
			trigger.accept(player);
			if (wanted.getLevel() == before) break;
		}

		return added;
	}

	/**
	 * Who saw it (0.21.2, PLAN.md section 3): a cop eyewitness is {@code Seen_By_Cop_Multiplier}, anyone else 1.0, and
	 * each eyewitness past the first adds {@code Per_Extra_Witness}, up to {@code Max_Extra_Witnesses}. The squad's
	 * fresh sighting stands in for a cop only on a crime the witness gate does not hold (T-538): a gated crime that
	 * lands was reported, and its report says whether a cop saw it.
	 */
	private double witnessFactor(CrimeCommittedEvent event, HeatSettings heat) {
		WitnessSettings witnesses = config.getWitnesses();
		boolean         gated     = witnesses.enabled() && witnesses.crimes().contains(event.getCrimeId());
		boolean         cop       = event.isSeenByCop() || (!gated && copSight.test(event.getPlayer()));
		int             extra     = Math.min(Math.max(0, event.getWitnesses() - 1), heat.maxExtraWitnesses());

		return (cop ? heat.seenByCopMultiplier() : 1.0) + heat.perExtraWitness() * extra;
	}

	/** Commits Assault_Cop unless this attacker already hit this victim inside {@code Heat.Assault_Repeat_Seconds}. */
	public void reportAssault(Player attacker, UUID victimId, Location at) {
		long now    = clock.getAsLong();
		long window = config.get().heat().assaultRepeatSeconds() * 1000L;

		Map<UUID, Long> victims = assaults.computeIfAbsent(attacker.getUniqueId(), id -> new ConcurrentHashMap<>());
		Long            last    = victims.get(victimId);
		if (last != null && now - last < window) return;

		victims.put(victimId, now);
		book(attacker, Crimes.ASSAULT_COP, at, victimId);
	}

	/**
	 * Commits Kill_Cop for {@code killer} at the body while the heat ledger is on (0.21.2, T-544). The cop's real death
	 * books it, so a lethal hit a shield absorbs is never a kill; with heat off the core kill combo counts it instead.
	 */
	public void reportCopKill(Player killer, @Nullable Location at, @Nullable UUID victimId) {
		if (!config.get().heat().enabled()) return;

		book(killer, Crimes.KILL_COP, at, victimId);
	}

	/** Commits a crime against cop {@code victimId}; {@link #currentVictim} names him while the commit runs. */
	private void book(Player player, String crimeId, @Nullable Location at, @Nullable UUID victimId) {
		booking = victimId;
		try {
			crimes.commit(player, crimeId, at);
		} finally {
			booking = null;
		}
	}

	public void onLevelChanged(UUID playerId, int oldLevel, int newLevel, int maxLevel, WantedCause cause) {
		HeatSettings heat = config.get().heat();

		if (newLevel < oldLevel) {
			Chase chase = chases.get(playerId);
			if (chase != null) chase.heat = Math.min(chase.heat, heat.floorOf(newLevel, maxLevel));
		} else if (newLevel > oldLevel && cause != WantedCause.CRIME) {
			Chase chase = chases.computeIfAbsent(playerId, id -> new Chase(0));
			chase.heat = Math.max(chase.heat, heat.floorOf(newLevel, maxLevel));
		}
	}

	/** The chase is over: forget its heat, crimes and assault cooldowns. */
	public void clear(UUID playerId) {
		chases.remove(playerId);
		assaults.remove(playerId);
	}

	public double heatOf(UUID playerId) {
		Chase chase = chases.get(playerId);
		return chase == null ? 0 : chase.heat;
	}

	public @Nullable CrimeRecord lastCrime(UUID playerId) {
		Chase chase = chases.get(playerId);
		return chase == null || chase.crimes.isEmpty() ? null : chase.crimes.get(chase.crimes.size() - 1);
	}

	/** Oldest first; an unmodifiable copy. */
	public List<CrimeRecord> chaseCrimes(UUID playerId) {
		Chase chase = chases.get(playerId);
		return chase == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(chase.crimes));
	}

	private static final class Chase {
		private double                  heat;
		private final List<CrimeRecord> crimes = new ArrayList<>();

		private Chase(double heat) {
			this.heat = heat;
		}
	}
}
