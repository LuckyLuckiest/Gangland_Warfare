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
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.HideoutSettings;
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
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

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
		/** Opened by the sighting a RESTORE start seeds, not by a cop: no respot, and its end is no loss of sight. */
		boolean      seed;
		long         insideMs;
		long         outsideMs;
		/** A long jump by command, plugin or portal during this spell. */
		boolean      teleported;
		/** The hideout that holds the search centre: he was seen walking in, so it never speeds the timer. */
		@Nullable String seenHideout;
	}

	/** The timer speed of one tick and its three factors; the total is capped at {@code Max_Speed}. */
	private record Speed(double total, double zone, double hideout, double quiet) {
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
	private final Hideouts            hideouts;
	private final QuietTrail          quiet;
	private final LongSupplier        clock;
	private final Consumer<Event>     callEvent;
	/** True while the player rides a live tracker-tagged vehicle: the clock holds, no decay. */
	private final Predicate<Player>   trackerLive;
	private final Map<UUID, Track>    tracks     = new ConcurrentHashMap<>();
	/** Players whose AUTO decision already failed once, so the warning is logged once each. */
	private final Set<UUID>           autoFailed = ConcurrentHashMap.newKeySet();
	/** Players whose clock is holding for units still on their way, so the debug line is logged once per hold. */
	private final Set<UUID>           holding    = ConcurrentHashMap.newKeySet();
	/** Players told this episode that no cop is on them ({@link #setNoCopSearch}); forgotten in {@link #clear}. */
	private final Set<UUID>           noCopTold  = ConcurrentHashMap.newKeySet();
	private volatile Consumer<Player> noCopSearch = player -> { };

	public EvasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                    WantedStars stars, UserManager<Player> users, HeatLedger ledger, ChaseArcs arcs,
	                    ChaseLearner learner, Hideouts hideouts, QuietTrail quiet, LongSupplier clock,
	                    Consumer<Event> callEvent) {
		this(config, copManager, detainment, stars, users, ledger, arcs, learner, hideouts, quiet, clock, callEvent,
		     p -> false);
	}

	public EvasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                    WantedStars stars, UserManager<Player> users, HeatLedger ledger, ChaseArcs arcs,
	                    ChaseLearner learner, Hideouts hideouts, QuietTrail quiet, LongSupplier clock,
	                    Consumer<Event> callEvent, Predicate<Player> trackerLive) {
		this.config      = config;
		this.trackerLive = trackerLive;
		this.copManager = copManager;
		this.detainment = detainment;
		this.stars      = stars;
		this.users      = users;
		this.ledger     = ledger;
		this.arcs       = arcs;
		this.learner    = learner;
		this.hideouts   = hideouts;
		this.quiet      = quiet;
		this.clock      = clock;
		this.callEvent  = callEvent;
	}

	/**
	 * While the module hunts him (a group exists) this clock owns his stars, cops standing or not: killing every cop never
	 * hands them to the core's Repeating_Timer decay (0.21.2, T-537). Only an escape, an arrest, a death or a command
	 * lowers them.
	 */
	@Override
	public boolean handlesDecay(Player player, Wanted wanted) {
		return config.get().evasion().enabled() && copManager.groupOf(player.getUniqueId()) != null;
	}

	/** The player's current state, or {@code null} when he is not tracked. */
	public @Nullable EvasionSnapshot snapshot(UUID playerId) {
		Track t = tracks.get(playerId);
		if (t == null) return null;

		return new EvasionSnapshot(t.state, t.level, Math.max(0, t.secondsLeft), t.centre, t.radius);
	}

	/**
	 * Fires OFF once when the player was tracked, then forgets him. Dropping a SEEN track (quit, squad dead or walking
	 * home) ends the contact, so it stamps the loss of sight the next, null-track search no longer can.
	 */
	public void clear(Player player) {
		holding.remove(player.getUniqueId());
		noCopTold.remove(player.getUniqueId());
		Track t = tracks.remove(player.getUniqueId());
		if (t == null) return;
		if (t.state == EvasionState.SEEN) arcs.lost(player.getUniqueId());

		User<Player> user  = users.getUser(player);
		int          level = user == null ? 0 : user.getWanted().getLevel();
		callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.OFF, level, 0, null, 0));
	}

	/**
	 * Tells a player the search runs with no cop standing ({@code Hud.No_Cop_Search}, 0.21.2 R2, T-586): once per
	 * episode, the first run the clock searches him with every cop dead or walking home.
	 */
	public void setNoCopSearch(Consumer<Player> noCopSearch) {
		this.noCopSearch = noCopSearch;
	}

	/** A long jump by command, plugin or portal: this spell can no longer be a clean break. */
	public void teleported(Player player) {
		Track t = tracks.get(player.getUniqueId());
		if (t != null) t.teleported = true;
	}

	public void tick(Player player, @Nullable CopGroup group) {
		EvasionSettings cfg  = config.get().evasion();
		User<Player>    user = users.getUser(player);
		long            now  = clock.getAsLong();
		// 0.21.2 (T-537): no cop standing is no clear; only the end of the hunt (no group) or of the stars is
		if (user == null || !cfg.enabled() || !user.getWanted().isWanted() || group == null) {
			clear(player);
			return;
		}

		Wanted wanted = user.getWanted();
		UUID   id     = player.getUniqueId();
		long   lostMs = cfg.lostSightSeconds() * 1000L;
		Track  track  = tracks.get(id);

		if (detainment.isRestrained(player) || group.tippedOffWithin(now, lostMs)) {
			if (track != null) track.lastTick = now;
			return;
		}
		// C-24: a live tracker tag on his vehicle holds the clock; the tag's own pings keep the squad on him
		if (trackerLive.test(player)) {
			if (track != null) track.lastTick = now;
			return;
		}
		// No cop stands: the bounded holds below pause the clock (its progress kept); otherwise he is searched at the
		// last-known spot at No_Cop_Inside_Speed (0.21.2, T-537)
		boolean standing = hasStandingCop(group);
		String  hold     = standing ? null : noCopHold(group, player, now);
		if (hold != null) {
			if (track != null) track.lastTick = now;
			if (holding.add(id)) log.debug("EVASION {} hold={}", player.getName(), hold);
			return;
		}
		holding.remove(id);

		int  level  = wanted.getLevel();
		long unseen = group.getSquad().millisSinceSighting();
		// the squad's latest sighting; meaningless (and never read) before the first one
		long seenAt = now - unseen;
		// a dead squad's last sighting is nobody's eyes on him: with no cop standing he is searched, never SEEN
		if (standing && unseen < lostMs) {
			boolean seed = arcs.restoreSeed(id, seenAt);
			if (track == null || track.state != EvasionState.SEEN) {
				track           = new Track();
				track.state     = EvasionState.SEEN;
				track.level     = level;
				track.seenSince = now;
				track.seed      = seed;
				tracks.put(id, track);
				// a rejoin's seed holds the clock but is no cop's eyes on him: no SEEN until one really sees him
				if (!seed) {
					arcs.seen(id);
					callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEEN, level, 0, null, 0));
				}
			} else if (track.seed && !seed) {
				// a cop really saw him while the rejoin's seed held the track
				track.seed      = false;
				track.seenSince = seenAt;
				arcs.seen(id);
				callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEEN, level, 0, null, 0));
			}
			track.lastTick = now;
			return;
		}
		// past every hold with no cop standing: the search runs on nobody's eyes (T-586)
		if (!standing && noCopTold.add(id)) noCopSearch.accept(player);

		// A new search starts at the last sighting; one that follows a drop keeps its centre.
		if (track == null || track.state == EvasionState.SEEN) {
			Track spell = track == null ? new Track() : track;
			// contact ends at the SEEN -> SEARCHING switch only; a null track (relog, replaced squad) or a rejoin's seed
			// was never in sight. In sight until the last sighting, not through the Lost_Sight_Seconds grace after it
			if (track != null && !track.seed) {
				spell.narrow = seenAt - spell.seenSince >= cfg.auto().momentum().narrowSeenSeconds() * 1000L;
				arcs.lost(id);
			}
			arcs.searchStarted(id);
			Location last = group.getSquad().lastKnownLocation();
			startSearch(player, spell, last != null ? last : player.getLocation(), level, cfg, now, standing);
			return;
		}
		if (track.state == EvasionState.EVADED) {
			startSearch(player, track, track.centre, level, cfg, now, standing);
			return;
		}

		long dt = Math.max(0, Math.min(now - track.lastTick, MAX_DT_MS));
		// read before track.level is overwritten
		boolean levelChanged = level != track.level;
		track.lastTick = now;
		track.level    = level;
		track.radius   = cfg.radiusFor(level);
		boolean inside = inside(player, track);
		Speed   speed  = speed(player, track, cfg, now, inside, standing);
		if (inside) {
			track.insideMs += dt;
		} else {
			track.outsideMs += dt;
		}
		track.progress += (long) (dt * speed.total());

		boolean auto = cfg.dropMode() == DropMode.AUTO;
		if (auto && levelChanged) track.needMs = autoNeed(player, track, cfg, level);
		// needMs 0 under AUTO: a reload flipped the mode mid-search, so today's timer until the next decision
		long need = auto && track.needMs > 0 ? track.needMs : cfg.secondsToDropFor(level) * 1000L;
		if (track.progress >= need) {
			int count   = auto ? autoCount(player, track, cfg, level)
			                   : (cfg.dropMode() == DropMode.ALL_STARS ? level : 1);
			int dropped = stars.drop(user, count, WantedCause.EVASION);
			track.progress = 0;
			// a cancelled drop never reached the HUD; its plan must not label a later drop
			if (auto && dropped == 0) arcs.takePending(id);
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

	private void startSearch(Player player, Track track, Location centre, int level, EvasionSettings cfg, long now,
	                         boolean standing) {
		track.state       = EvasionState.SEARCHING;
		track.level       = level;
		track.centre      = centre;
		track.radius      = cfg.radiusFor(level);
		track.progress    = 0;
		track.lastTick    = now;
		track.secondsLeft = -1;
		track.needMs      = autoNeed(player, track, cfg, level);
		track.seenHideout = hideouts.idAt(centre, player.getUniqueId());
		tracks.put(player.getUniqueId(), track);
		long need = track.needMs > 0 ? track.needMs : cfg.secondsToDropFor(level) * 1000L;
		fireCountdown(player, track, need, speed(player, track, cfg, now, inside(player, track), standing));
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
		HeatSettings heat  = Objects.requireNonNullElse(config.get().heat(), HeatSettings.DEFAULT);
		ChaseView    chase = arcs.view(id, ledger.chaseCrimes(id), auto, heat::weightOf);
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
	private void fireCountdown(Player player, Track track, long needMs, Speed speed) {
		int left = (int) Math.ceil((needMs - track.progress) / 1000.0 / speed.total());
		if (left == track.secondsLeft) return;

		track.secondsLeft = left;
		log.debug("EVASION {} speed={} zone={} hideout={} quiet={}", player.getName(), format(speed.total()),
		          format(speed.zone()), format(speed.hideout()), format(speed.quiet()));
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

	/**
	 * Zone x hideout x cold trail, capped at {@code Max_Speed}. The zone is 1 inside ({@code No_Cop_Inside_Speed} while no
	 * cop stands, 0.21.2), {@code Outside_Zone_Speed} outside; a hideout counts only when it is not the one that holds the
	 * last sighting; the cold trail only ever speeds up (a floor of 1 also keeps a failed {@link QuietTrail} from
	 * freezing the clock). Every factor and the total are floored at 0.01, so the clock never freezes.
	 */
	private Speed speed(Player player, Track track, EvasionSettings cfg, long now, boolean inside, boolean standing) {
		double          zone = inside ? (standing ? 1.0 : Math.max(0.01, cfg.noCopInsideSpeed()))
		                              : Math.max(0.01, cfg.outsideZoneSpeed());
		HideoutSettings h    = Objects.requireNonNullElse(cfg.hideout(), HideoutSettings.DEFAULT);
		PlaceRegion     spot = h.enabled() ? hideouts.at(player) : null;
		double          hide = spot != null && !spot.id().equals(track.seenHideout) ? h.speed() : 1.0;
		double          cold = Math.max(1.0, quiet.speed(player.getUniqueId(), now));
		return new Speed(Math.max(0.01, Math.min(cfg.maxSpeed(), zone * hide * cold)), zone, hide, cold);
	}

	/**
	 * Why the clock holds while no cop stands, or {@code null} to search him. Every hold is bounded:
	 * <ul>
	 *   <li>{@code enroute}: the first response, units on their way (or the rejoin grace) before any cop of the episode
	 *   stood; it lapses {@code EN_ROUTE_GRACE_MS} past their ETA. After a wipe the breather and the ETA no longer hold
	 *   (0.21.2).</li>
	 *   <li>{@code handoff}: an active hand-off bias, or a leashed walker past {@code Pursuit.Max_Distance} still hunting
	 *   him, which the next spawn task releases and refills (0.21.1 cause E, T-400/T-412); a walker inside the range is
	 *   not refilled, so it holds nothing.</li>
	 *   <li>{@code silence}: a cop of the group killed where nobody saw it, whose silence dispatch has not noticed yet
	 *   (0.21.2 S5, T-545); it lapses at Officer_Missing, {@code Check_In.Silent_Seconds} past his last check-in.</li>
	 * </ul>
	 */
	private @Nullable String noCopHold(CopGroup group, Player player, long now) {
		// the rejoin grace (R21) counts too: a RESTORE start tips nothing, so before the spawn task queues its units
		// nothing else would stop a search ring round his live spot
		if (!group.hasStood() && (group.unitsEnRoute(now) || now < group.getBreatherUntil())) return "enroute";
		if (group.biasAt(now) != null || hasLeashedWalker(group, player)) return "handoff";
		// dispatch still thinks a cop killed unseen is on him
		if (!copManager.silentRoles(group).isEmpty()) return "silence";
		return null;
	}

	private boolean hasLeashedWalker(CopGroup group, Player player) {
		for (CopNpc cop : group.getCops()) {
			if (cop.isValid() && player.getUniqueId().equals(cop.getTargetPlayerId()) &&
			    copManager.isStranded(cop, player)) return true;
		}
		return false;
	}

	private static boolean hasStandingCop(@Nullable CopGroup group) {
		if (group == null) return false;

		for (CopNpc cop : group.getCops()) {
			if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) return true;
		}
		return false;
	}
}
