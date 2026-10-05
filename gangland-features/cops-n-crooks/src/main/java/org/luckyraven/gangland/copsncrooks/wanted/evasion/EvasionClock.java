package org.luckyraven.gangland.copsncrooks.wanted.evasion;

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
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.DropMode;
import org.luckyraven.gangland.copsncrooks.wanted.config.EvasionSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedDecayPolicy;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Line-of-sight evasion: while cops hunt a player the safety-net timer is switched off and this clock lowers his stars
 * instead. Fed once per cop AI tick through {@link CopManager#addAiTickHook}; main thread only.
 *
 * @since 0.15.0
 */
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
	}

	private final ChaseConfigLoader   config;
	private final CopManager          copManager;
	private final DetainmentService   detainment;
	private final WantedStars         stars;
	private final UserManager<Player> users;
	private final LongSupplier        clock;
	private final Consumer<Event>     callEvent;
	private final Map<UUID, Track>    tracks = new ConcurrentHashMap<>();

	public EvasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment,
	                    WantedStars stars, UserManager<Player> users, LongSupplier clock, Consumer<Event> callEvent) {
		this.config     = config;
		this.copManager = copManager;
		this.detainment = detainment;
		this.stars      = stars;
		this.users      = users;
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
				track       = new Track();
				track.state = EvasionState.SEEN;
				track.level = level;
				tracks.put(id, track);
				callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEEN, level, 0, null, 0));
			}
			track.lastTick = now;
			return;
		}

		// A new search starts at the last sighting; one that follows a drop keeps its centre.
		if (track == null || track.state == EvasionState.SEEN) {
			Location last = group.getSquad().lastKnownLocation();
			startSearch(player, track == null ? new Track() : track, last != null ? last : player.getLocation(), level,
			            cfg, now);
			return;
		}
		if (track.state == EvasionState.EVADED) {
			startSearch(player, track, track.centre, level, cfg, now);
			return;
		}

		long dt = Math.max(0, Math.min(now - track.lastTick, MAX_DT_MS));
		track.lastTick = now;
		track.level    = level;
		track.radius   = cfg.radiusFor(level);
		double speed = speed(player, track, cfg);
		track.progress += (long) (dt * speed);

		long need = cfg.secondsToDropFor(level) * 1000L;
		if (track.progress >= need) {
			int dropped = stars.drop(user, cfg.dropMode() == DropMode.ALL_STARS ? level : 1, WantedCause.EVASION);
			track.progress = 0;
			// The last star ends the chase: the WantedEndEvent listener has already cleared him and fired OFF.
			if (!wanted.isWanted() || dropped == 0) return;

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
		tracks.put(player.getUniqueId(), track);
		fireCountdown(player, track, cfg.secondsToDropFor(level) * 1000L, speed(player, track, cfg));
	}

	/** Fires SEARCHING with the seconds left, but only when that number changed. */
	private void fireCountdown(Player player, Track track, long needMs, double speed) {
		int left = (int) Math.ceil((needMs - track.progress) / 1000.0 / speed);
		if (left == track.secondsLeft) return;

		track.secondsLeft = left;
		callEvent.accept(new WantedEvasionStateEvent(player, EvasionState.SEARCHING, track.level, left, track.centre,
		                                             track.radius));
	}

	/** 1 inside the search zone, {@code Outside_Zone_Speed} outside it (another world counts as outside). */
	private static double speed(Player player, Track track, EvasionSettings cfg) {
		Location at     = player.getLocation();
		World    world  = at.getWorld();
		Location centre = track.centre;
		boolean  inside = centre != null && world != null && world.equals(centre.getWorld())
		                  && at.distance(centre) <= track.radius;
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
