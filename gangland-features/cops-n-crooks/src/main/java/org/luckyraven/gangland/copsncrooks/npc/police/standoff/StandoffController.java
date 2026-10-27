package org.luckyraven.gangland.copsncrooks.npc.police.standoff;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PerimeterSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PerimeterController;
import org.luckyraven.gangland.copsncrooks.npc.police.perimeter.PostRing;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The barricade standoff (0.21.0): when the squad cannot reach a suspect it posts a ring, takes cover, holds the refill,
 * negotiates on the radio and raises a star at the timeout.
 *
 * @since 0.21.0
 */
@CustomLog
public class StandoffController {

	/** Per running standoff. */
	private static final class State {

		final Player   player;
		final CopGroup group;
		long until;
		long nextNegotiateAt;
		final List<CopNpc> posts = new ArrayList<>();

		State(Player player, CopGroup group, long until) {
			this.player = player;
			this.group = group;
			this.until = until;
		}
	}

	private record Casualty(long at, Location where) {}

	private final Supplier<CopConfigProvider> provider;
	private final CopRadio                    radio;
	private final PerimeterController         perimeter;
	private final WantedStars                 stars;
	private final UserManager<Player>         users;
	private final Predicate<Player>           restrained;
	private final LongSupplier                clock;

	private final Map<UUID, State>           active        = new HashMap<>();
	private final Map<UUID, Deque<Casualty>> casualties    = new HashMap<>();
	private final Map<UUID, Long>            cooldownUntil = new HashMap<>();

	public StandoffController(Supplier<CopConfigProvider> provider, CopRadio radio, PerimeterController perimeter,
	                          WantedStars stars, UserManager<Player> users, Predicate<Player> restrained,
	                          LongSupplier clock) {
		this.provider = provider;
		this.radio = radio;
		this.perimeter = perimeter;
		this.stars = stars;
		this.users = users;
		this.restrained = restrained;
		this.clock = clock;
	}

	private StandoffSettings settings() {
		CopConfigProvider p = provider == null ? null : provider.get();
		StandoffSettings  s = p == null ? null : p.getStandoffSettings();
		return s == null ? StandoffSettings.DEFAULT : s;
	}

	/** AI tick hook: starts, runs (negotiate, timeout) or ends the suspect's standoff. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID  id = player.getUniqueId();
		State st = active.get(id);
		if (group == null) {
			if (st != null) end(id, "GONE");
			return;
		}
		if (restrained.test(player) || group.isCeaseFire()) {
			if (st != null) end(id, "SURRENDER");
			return;
		}
		StandoffSettings cfg = settings();
		long             now = clock.getAsLong();
		if (st != null) {
			if (group != st.group || !group.isStandoff(st.until - 1) || !cfg.enabled()) {
				end(id, "GONE");
				return;
			}
			activeTick(st, cfg, now);
			return;
		}
		prune(id, cfg, now);
		if (!cfg.enabled() || group.isEmpty() || now < cooldownUntil.getOrDefault(id, 0L)) return;
		if (cuffing(group, id)) return;

		List<CopNpc> hunting = hunting(group, id, player.getLocation());
		if (hunting.size() < 2) return;
		String trigger = trigger(cfg, group, id, hunting, now);
		if (trigger != null) start(player, group, cfg, hunting, trigger, now);
	}

	/** Keeps the refill held and radios the countdown. */
	private void activeTick(State st, StandoffSettings cfg, long now) {
		reportPostedSightings(st);
		if (now >= st.until) {
			timeout(st, cfg);
			return;
		}
		holdRefill(st.group, now, st.until);
		if (now >= st.nextNegotiateAt) {
			long left = (st.until - now + 999) / 1000;
			radio.sayFromLeader(st.group, "Negotiate", Map.of("seconds", String.valueOf(left)));
			st.nextNegotiateAt += cfg.negotiateSeconds() * 1000L;
		}
	}

	/** A posted cop that still sees the suspect reports him, keeping the squad's sighting fresh; the post is kept. */
	private void reportPostedSightings(State st) {
		for (CopNpc cop : st.posts) {
			if (cop == null || !cop.isValid() || cop.getCurrentState() != CopState.POSTED) continue;
			double range = sightRange();
			if (cop.canSee(st.player, range)) {
				st.group.getSquad().reportSighting(cop, st.player.getLocation(), range);
				return;
			}
		}
	}

	private double sightRange() {
		CopConfigProvider p = provider == null ? null : provider.get();
		PerimeterSettings s = p == null ? null : p.getPerimeterSettings();
		return (s == null ? PerimeterSettings.DEFAULT : s).sightRange();
	}

	/** ponytail: rolling hold, a short step ahead of now, so the end paths need no cancel of a long hold. */
	private static final long HOLD_STEP_MS = 5000L;

	private static void holdRefill(CopGroup group, long now, long until) {
		group.holdPendingUntil(Math.min(until, now + HOLD_STEP_MS));
	}

	/** A squad cop mid-arrest on this suspect: the arrest, not a standoff, resolves the encounter. */
	private static boolean cuffing(CopGroup group, UUID id) {
		for (CopNpc cop : group.getCops()) {
			if (cop == null || !cop.isValid()) continue;
			if (cop.getCurrentState() == CopState.CUFFING && id.equals(cop.getTargetPlayerId())) return true;
		}
		return false;
	}

	private static List<CopNpc> hunting(CopGroup group, UUID id, @Nullable Location at) {
		List<CopNpc> hunting = new ArrayList<>();
		World        world   = at == null ? null : at.getWorld();
		for (CopNpc cop : group.getCops()) {
			if (cop == null || !cop.isValid()) continue;
			CopState state = cop.getCurrentState();
			if (state != CopState.PURSUING && state != CopState.COMBAT) continue;
			if (world != null && !world.equals(cop.getEntity().getLocation().getWorld())) continue;
			if (id.equals(cop.getTargetPlayerId())) hunting.add(cop);
		}
		return hunting;
	}

	@Nullable
	private String trigger(StandoffSettings cfg, CopGroup group, UUID id, List<CopNpc> hunting, long now) {
		NpcSquad squad = group.getSquad();
		// cops that cannot reach a spot nobody sees him at are searching, not besieging him (radio-far S)
		if (squad.isInContact() || squad.hasFreshSighting()) {
			long stuck = 0;
			for (CopNpc cop : hunting)
				if (cop.millisUnreachable() >= cfg.unreachableSeconds() * 1000L) stuck++;
			if (stuck * 2 > hunting.size()) return "UNREACHABLE";
		}

		if (squad.hasFreshSighting())
			for (CopNpc cop : hunting)
				if (cop.isNavigationHopeless()) return "NO_ROUTE";

		Deque<Casualty> dead = casualties.get(id);
		if (dead != null && !dead.isEmpty()) {
			Casualty last = dead.peekLast();
			long     near = dead.stream().filter(c -> now - c.at() <= cfg.manDownWindowSeconds() * 1000L &&
			                                           sameSpot(c.where(), last.where(), cfg.spotRadius())).count();
			if (near >= cfg.manDownCount()) return "MAN_DOWN";
		}
		return null;
	}

	private static boolean sameSpot(Location a, Location b, double radius) {
		try {
			return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) <= radius * radius;
		} catch (IllegalArgumentException unloaded) { // Location.getWorld throws once its world is unloaded
			return false;
		}
	}

	/** Flags, ring posts, radio, negotiate cadence and the refill hold. */
	private void start(Player player, CopGroup group, StandoffSettings cfg, List<CopNpc> hunting, String trigger,
	                   long now) {
		long  until = now + cfg.timerSeconds() * 1000L;
		UUID  id    = player.getUniqueId();
		State st    = new State(player, group, until);
		st.nextNegotiateAt = now;
		active.put(id, st);
		group.setStandoffUntil(until);
		group.setFallBackUntil(until);
		holdRefill(group, now, until);

		// where the squad knows him to be, never his live position (radio-far S)
		Location centre = group.getSquad().lastKnownLocation();
		if (!perimeter.isActive(id) && centre != null && centre.getWorld() != null) post(st, cfg, hunting, centre);
		radio.sayFromLeader(group, "Standoff");
		log.debug("STANDOFF {} start trigger={}", player.getName(), trigger);
		activeTick(st, cfg, now);
	}

	private static void post(State st, StandoffSettings cfg, List<CopNpc> hunting, Location centre) {
		List<Location> spots = PostRing.find(centre, cfg.postRadius(), cfg.postCount(), cfg.postRadius());
		List<CopNpc>   byDistance = new ArrayList<>(hunting);
		byDistance.sort(Comparator.comparingDouble(c -> c.getEntity().getLocation().distanceSquared(centre)));
		for (int i = 0; i < spots.size() && i < byDistance.size(); i++) {
			CopNpc cop = byDistance.get(i);
			cop.transitionTo(CopState.POSTED);
			cop.holdPost(new NpcPost(spots.get(i), cfg.postRadius(), centre));
			st.posts.add(cop);
		}
	}

	private void timeout(State st, StandoffSettings cfg) {
		radio.sayFromLeader(st.group, "Standoff_Over");
		var user = users.getUser(st.player);
		if (user != null && cfg.starsOnTimeout() > 0) stars.raise(user, cfg.starsOnTimeout(), WantedCause.CRIME);
		end(st.player.getUniqueId(), "TIMEOUT");
	}

	private void prune(UUID id, StandoffSettings cfg, long now) {
		Long until = cooldownUntil.get(id);
		if (until != null && now >= until) cooldownUntil.remove(id);
		Deque<Casualty> dead = casualties.get(id);
		if (dead == null) return;
		dead.removeIf(c -> now - c.at() > cfg.manDownWindowSeconds() * 1000L);
		if (dead.isEmpty()) casualties.remove(id);
	}

	private void end(UUID id, String reason) {
		State st = active.remove(id);
		if (st == null) return;
		release(st);
		casualties.remove(id);
		cooldownUntil.put(id, clock.getAsLong() + settings().cooldownSeconds() * 1000L);
		log.debug("STANDOFF {} end outcome={}", st.player.getName(), reason);
	}

	private static void release(State st) {
		for (CopNpc cop : st.posts) {
			if (cop == null || !cop.isValid() || cop.getCurrentState() != CopState.POSTED) continue;
			cop.releasePost();
			cop.transitionTo(CopState.PURSUING);
		}
		st.group.setStandoffUntil(0L);
		st.group.setFallBackUntil(0L);
	}

	/** Quit or logout: drops the suspect's standoff state with no cooldown, so his next chase can start one at once. */
	public void forget(UUID id) {
		State st = active.remove(id);
		if (st != null) release(st);
		casualties.remove(id);
		cooldownUntil.remove(id);
	}

	/** Cop-attacked hook: the suspect hit a cop, so his standoff ends (ESCALATE). */
	public void onCopAttacked(CopNpc cop, Player attacker) {
		if (active.containsKey(attacker.getUniqueId())) end(attacker.getUniqueId(), "ESCALATE");
	}

	/** A cop of the suspect's squad died at {@code where}; feeds the man-down trigger. */
	public void recordCasualty(UUID suspectId, Location where) {
		casualties.computeIfAbsent(suspectId, k -> new ArrayDeque<>()).addLast(new Casualty(clock.getAsLong(), where));
	}

	public boolean isActive(UUID suspectId) {
		return active.containsKey(suspectId);
	}
}
