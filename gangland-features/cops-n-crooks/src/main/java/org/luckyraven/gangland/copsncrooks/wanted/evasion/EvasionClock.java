package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.config.AutoSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.ChaseView;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Learned;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.SpellView;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLearner;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedDecayPolicy;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Line-of-sight evasion: while cops hunt a player the safety-net timer is switched off and this clock lowers his stars
 * instead. Fed once per cop AI tick through {@link CopManager#addAiTickHook}; main thread only.
 *
 * <p>{@code Drop_Mode: AUTO} (0.15.2) asks {@link AutoDropPlanner} how many stars a completed evasion takes and how
 * long each hide timer is, from the {@link ChaseArcs}, the heat ledger's crimes and what the {@link ChaseLearner} has
 * learned. Any failure there falls back to ONE_STAR for that decision.
 *
 * @since 0.15.0
 */
@CustomLog
public final class EvasionClock implements WantedDecayPolicy {

	/** Longest gap one tick may count, so a stalled server never hands out a free star. */
	private static final long MAX_DT_MS = 1000L;

	private static final class Track {

		EvasionState state;
		int          level;
		int          secondsLeft = -1;
		Location     centre;
		double       radius;
		long         progress;
		long         lastTick;
		/** AUTO's timer for this spell; 0 = {@code Seconds_To_Drop} (any other mode, or a reload flipped to AUTO). */
		long         needMs;
		/** Stars lost since the last SEEN. */
		int          steps;
		/** He had been in sight {@code Narrow_Seen_Seconds} straight before he broke away. */
		boolean      narrow;
		long         seenSince;
		long         insideMs;
		long         outsideMs;
		/** A long jump by command, plugin or portal during this spell. */
		boolean      teleported;
	}

	private record Views(ChaseView chase, SpellView spell, Learned learned) {
	}

	private final ChaseConfigLoader   config;
	private final CopManager          copManager;
	private final DetainmentService   detainment;
	private final WantedStars         stars;
	private final UserManager<Player> users;
	private final HeatLedger          ledger;
	private final ChaseArcs           arcs;
	private final ChaseLearner        learner;
	private final LongSupplier        clock;
	private final Consumer<Event>     callEvent;
	private final Map<UUID, Track>    tracks     = new ConcurrentHashMap<>();
	/** Players whose AUTO decision already failed once, so the warning is logged once each. */
	private final Set<UUID>           autoFailed = ConcurrentHashMap.newKeySet();

	public EvasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                    WantedStars stars, UserManager<Player> users, HeatLedger ledger, ChaseArcs arcs,
	                    ChaseLearner learner, LongSupplier clock, Consumer<Event> callEvent) {
		this.config     = config;
		this.copManager = copManager;
		this.detainment = detainment;
		this.stars      = stars;
		this.users      = users;
		this.ledger     = ledger;
		this.arcs       = arcs;
		this.learner    = learner;
		this.clock      = clock;
		this.callEvent  = callEvent;
	}

	@Override
	public boolean handlesDecay(Player player, Wanted wanted) {
		return config.get().evasion().enabled() && hasLiveCop(copManager.groupOf(player.getUniqueId()));
	}

	/** The player's current state, or {@code null} when he is not tracked. */
	public @Nullable EvasionSnapshot snapshot(UUID playerId) {
		Track t = tracks.get(playerId);
		if (t == null) return null;

		return new EvasionSnapshot(t.state, t.level, Math.max(0, t.secondsLeft), t.centre, t.radius);
	}

	/** Fires OFF once when the player was tracked, then forgets him. */
	public void clear(Player player) {
		Track t = tracks.remove(player.getUniqueId());
		if (t == null) return;

		User<Player> user  = users.getUser(player);
		int          level = user == null ? 0 : user.getWanted().getLevel();
		callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.OFF, level, 0, null, 0));
	}

	/** A long jump by command, plugin or portal: this spell can no longer be a clean break. */
	public void teleported(Player player) {
		Track t = tracks.get(player.getUniqueId());
		if (t != null) t.teleported = true;
	}

	public void tick(Player player, @Nullable CopGroup group) {
		EvasionSettings cfg  = config.get().evasion();
		User<Player>    user = users.getUser(player);
		if (user == null || !cfg.enabled() || !user.getWanted().isWanted() || !hasLiveCop(group)) {
			clear(player);
			return;
		}

		Wanted wanted = user.getWanted();
		UUID   id     = player.getUniqueId();
		long   now    = clock.getAsLong();
		long   lostMs = cfg.lostSightSeconds() * 1000L;
		Track  track  = tracks.get(id);

		if (detainment.isRestrained(player) || group.tippedOffWithin(now, lostMs)) {
			if (track != null) track.lastTick = now;
			return;
		}

		int level = wanted.getLevel();
		if (group.getSquad().millisSinceSighting() < lostMs) {
			if (track == null || track.state != EvasionState.SEEN) {
				track           = new Track();
				track.state     = EvasionState.SEEN;
				track.level     = level;
				track.seenSince = now;
				tracks.put(id, track);
				arcs.seen(id);
				callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEEN, level, 0, null, 0));
			}
			track.lastTick = now;
			return;
		}

		// A new search starts at the last sighting; one that follows a drop keeps its centre.
		if (track == null || track.state == EvasionState.SEEN) {
			Track spell = track == null ? new Track() : track;
			if (track != null) {
				spell.narrow = now - spell.seenSince >= cfg.auto().momentum().narrowSeenSeconds() * 1000L;
			}
			arcs.lost(id);
			arcs.searchStarted(id);
			Location last = group.getSquad().lastKnownLocation();
			startSearch(player, spell, last != null ? last : player.getLocation(), level, cfg, now);
			return;
		}
		if (track.state == EvasionState.EVADED) {
			startSearch(player, track, track.centre, level, cfg, now);
			return;
		}

		long dt = Math.max(0, Math.min(now - track.lastTick, MAX_DT_MS));
		// read before track.level is overwritten
		boolean levelChanged = level != track.level;
		track.lastTick = now;
		track.level    = level;
		track.radius   = cfg.radiusFor(level);
		boolean inside = inside(player, track);
		double  speed  = speed(inside, cfg);
		if (inside) {
			track.insideMs += dt;
		} else {
			track.outsideMs += dt;
		}
		track.progress += (long) (dt * speed);

		boolean auto = cfg.dropMode() == DropMode.AUTO;
		if (auto && levelChanged) track.needMs = autoNeed(player, track, cfg, level);
		// needMs 0 under AUTO: a reload flipped the mode mid-search, so today's timer until the next decision
		long need = auto && track.needMs > 0 ? track.needMs : cfg.secondsToDropFor(level) * 1000L;
		if (track.progress >= need) {
			int count   = auto ? autoCount(player, track, cfg, level)
			                   : (cfg.dropMode() == DropMode.ALL_STARS ? level : 1);
			int dropped = stars.drop(user, count, WantedCause.EVASION);
			track.progress = 0;
			// The last star ends the chase: the WantedEndEvent listener has already cleared him and fired OFF.
			if (!wanted.isWanted() || dropped == 0) return;

			track.steps++;
			track.state       = EvasionState.EVADED;
			track.level       = wanted.getLevel();
			track.radius      = cfg.radiusFor(track.level);
			track.secondsLeft = 0;
			callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.EVADED, track.level, 0, track.centre,
			                                             track.radius));
			return;
		}

		fireCountdown(player, track, need, speed);
	}

	private void startSearch(Player player, Track track, Location centre, int level, EvasionSettings cfg, long now) {
		track.state       = EvasionState.SEARCHING;
		track.level       = level;
		track.centre      = centre;
		track.radius      = cfg.radiusFor(level);
		track.progress    = 0;
		track.lastTick    = now;
		track.secondsLeft = -1;
		track.needMs      = autoNeed(player, track, cfg, level);
		tracks.put(player.getUniqueId(), track);
		long need = track.needMs > 0 ? track.needMs : cfg.secondsToDropFor(level) * 1000L;
		fireCountdown(player, track, need, speed(inside(player, track), cfg));
	}

	/** AUTO's hide timer for this spell at {@code level}; 0 (today's {@code Seconds_To_Drop}) in any other mode. */
	private long autoNeed(Player player, Track track, EvasionSettings cfg, int level) {
		if (cfg.dropMode() != DropMode.AUTO) return 0;

		try {
			Views views = views(player.getUniqueId(), track, cfg.auto());
			if (views == null) return 0;

			double factor = AutoDropPlanner.factor(cfg.auto(), views.chase(), views.spell(), views.learned());
			return Math.max(1000L, (long) (cfg.secondsToDropFor(level) * 1000L * factor));
		} catch (RuntimeException exception) {
			autoFailed(player, exception);
			return 0;
		}
	}

	/** AUTO's star count for the drop now due; stashes the plan for the HUD. One star (ONE_STAR) on any failure. */
	private int autoCount(Player player, Track track, EvasionSettings cfg, int level) {
		UUID id = player.getUniqueId();
		try {
			AutoSettings auto  = cfg.auto();
			Views        views = views(id, track, auto);
			if (views == null) return 1;

			ChaseView c    = views.chase();
			SpellView sp   = views.spell();
			Learned   l    = views.learned();
			DropPlan  plan = AutoDropPlanner.plan(auto, c, sp, l, level);
			long      all  = sp.insideMs() + sp.outsideMs();
			log.debug("AUTO drop {} level={}->{} ending={} reason={} crimes={} opening={} peak={} chase={}s quiet={}s "
			          + "quits={} respots={} outside={} teleported={} steps={} narrow={} delta={} typical={}s factor={}",
			          player.getName(), level, level - plan.stars(), plan.ending(), plan.reason(), c.crimes(),
			          c.opening(), c.peak(), c.chaseMs() / 1000, c.quietMs() / 1000, c.quits(), c.respots(),
			          format(all == 0 ? 0 : (double) sp.outsideMs() / all), sp.teleported(), sp.steps(), sp.narrow(),
			          format(l.delta()), format(l.typicalSeconds()), format(AutoDropPlanner.factor(auto, c, sp, l)));
			arcs.stashPending(id, plan);
			return plan.stars();
		} catch (RuntimeException exception) {
			autoFailed(player, exception);
			return 1;
		}
	}

	/** The planner's inputs, or {@code null} when the chase has no arc (AUTO then behaves as ONE_STAR). */
	private @Nullable Views views(UUID id, Track track, AutoSettings auto) {
		ChaseView chase = arcs.view(id, ledger.chaseCrimes(id), auto);
		if (chase == null) return null;

		SpellView spell = new SpellView(track.insideMs, track.outsideMs, track.steps, track.narrow, track.teleported);
		return new Views(chase, spell, new Learned(learner.delta(id), learner.typicalSeconds(chase.peak(), auto)));
	}

	private void autoFailed(Player player, RuntimeException exception) {
		if (autoFailed.add(player.getUniqueId())) {
			log.warn("Drop_Mode AUTO failed for " + player.getName() + "; his drops fall back to ONE_STAR", exception);
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Fires SEARCHING with the seconds left, but only when that number changed. */
	private void fireCountdown(Player player, Track track, long needMs, double speed) {
		int left = (int) Math.ceil((needMs - track.progress) / 1000.0 / speed);
		if (left == track.secondsLeft) return;

		track.secondsLeft = left;
		callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, track.level, left, track.centre,
		                                             track.radius));
	}

	/** Inside the search zone; another world counts as outside. */
	private static boolean inside(Player player, Track track) {
		Location at     = player.getLocation();
		World    world  = at.getWorld();
		Location centre = track.centre;
		return centre != null && world != null && world.equals(centre.getWorld()) && at.distance(centre) <= track.radius;
	}

	/** 1 inside the search zone, {@code Outside_Zone_Speed} outside it. */
	private static double speed(boolean inside, EvasionSettings cfg) {
		return inside ? 1.0 : Math.max(0.01, cfg.outsideZoneSpeed());
	}

	private static boolean hasLiveCop(@Nullable CopGroup group) {
		if (group == null) return false;

		for (CopNpc cop : group.getCops()) {
			if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) return true;
		}
		return false;
	}
}
