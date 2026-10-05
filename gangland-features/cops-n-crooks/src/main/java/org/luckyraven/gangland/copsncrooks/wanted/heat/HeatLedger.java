package org.luckyraven.gangland.copsncrooks.wanted.heat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
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
		if (event.isSeenByCop() || copSight.test(player)) mult *= heat.seenByCopMultiplier();
		if (Crimes.KILL_PLAYER.equals(event.getCrimeId()) && contestedTurf.test(event.getLocation())) {
			mult *= heat.turfWarMultiplier();
		}

		double added = weight * mult;
		chase.heat += added;
		chase.crimes.add(new CrimeRecord(event.getCrimeId(), added, now, event.getLocation()));

		int target = heat.starsFor(chase.heat, maxLevel);
		Consumer<Player> trigger = starTrigger;
		for (int i = 0; trigger != null && i < maxLevel && wanted.getLevel() < target; i++) {
			int before = wanted.getLevel();
			trigger.accept(player);
			if (wanted.getLevel() == before) break;
		}

		return added;
	}

	/** Commits Assault_Cop unless this attacker already hit this victim inside {@code Heat.Assault_Repeat_Seconds}. */
	public void reportAssault(Player attacker, UUID victimId, Location at) {
		long now    = clock.getAsLong();
		long window = config.get().heat().assaultRepeatSeconds() * 1000L;

		Map<UUID, Long> victims = assaults.computeIfAbsent(attacker.getUniqueId(), id -> new ConcurrentHashMap<>());
		Long            last    = victims.get(victimId);
		if (last != null && now - last < window) return;

		victims.put(victimId, now);
		crimes.commit(attacker, Crimes.ASSAULT_COP, at);
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
