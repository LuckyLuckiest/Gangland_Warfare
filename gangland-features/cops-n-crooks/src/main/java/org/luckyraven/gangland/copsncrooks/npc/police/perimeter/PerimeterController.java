package org.luckyraven.gangland.copsncrooks.npc.police.perimeter;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PerimeterSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcPost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNullElse;

/**
 * The containment perimeter (0.16.0): when a wanted player drops out of sight, up to {@code Cops.Perimeter.Posts} of the
 * cops chasing him hold posts on a ring around the zone centre and watch for him. A post that sees him reports it, radios
 * and goes back to the chase; the perimeter ends when he is seen again, the clock ends, or {@code Max_Seconds} pass.
 * <p>
 * Only cops whose target is the player are posted ({@code CopManager.fightResisting} reaches only those), and the last
 * free cop is never posted. Main thread only, like the cop AI tick that drives {@link #tick}.
 *
 * @since 0.16.0
 */
@CustomLog
public class PerimeterController {

	/** Fraction of the sight range a post may sit from the zone centre, so it can see the centre. */
	private static final double RING_SIGHT_FRACTION = 0.8;

	private final Supplier<CopConfigProvider> provider;
	private final CopManager                  copManager;
	private final CopRadio                    copRadio;
	private final LongSupplier                clock;

	private final Map<UUID, Perimeter> active = new HashMap<>();
	/** Players whose perimeter ran out; no new one until {@link #end} (SEEN or OFF) clears the mark. */
	private final Set<UUID>            spent  = new HashSet<>();

	public PerimeterController(Supplier<CopConfigProvider> provider, CopManager copManager, CopRadio copRadio,
	                           LongSupplier clock) {
		this.provider   = provider;
		this.copManager = copManager;
		this.copRadio   = copRadio;
		this.clock      = clock;
	}

	/** Posts cops around {@code centre}; a no-op when disabled, below {@code Min_Level}, already active or spent. */
	public void start(Player player, Location centre, double radius, int level) {
		PerimeterSettings settings = settings();
		UUID              id       = player.getUniqueId();
		if (!settings.enabled() || level < settings.minLevel() || active.containsKey(id) || spent.contains(id)) return;

		CopGroup group = copManager.groupOf(id);
		if (group == null) return;

		List<CopNpc> candidates = candidates(group, id, settings.roles(), centre);
		int          wanted     = Math.min(settings.posts(), candidates.size() - 1);
		if (wanted <= 0) return;

		double         ringRadius = Math.min(radius, RING_SIGHT_FRACTION * settings.sightRange());
		List<Location> spots      = PostRing.find(centre, ringRadius, wanted, settings.laneLength());
		if (spots.isEmpty()) return;

		List<CopNpc> posts = new ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			CopNpc   cop  = candidates.get(i);
			Location spot = spots.get(i);

			cop.holdPost(new NpcPost(spot, settings.leashRadius(), centre));
			cop.transitionTo(CopState.POSTED);
			copRadio.sayAs(group, cop, "Post_Up", Map.of("place", copRadio.placeOf(spot)));
			posts.add(cop);
		}
		active.put(id, new Perimeter(posts, clock.getAsLong()));
		log.debug("PERIMETER {} start posts={} radius={}", player.getName(), posts.size(), ringRadius);
	}

	/** Sends every post still posted to {@code postsTo} (releasing it) and clears the perimeter and its spent mark. */
	public void end(Player player, CopState postsTo) {
		UUID id = player.getUniqueId();
		spent.remove(id);
		Perimeter perimeter = active.remove(id);
		if (perimeter == null) return;

		release(perimeter, postsTo);
		log.debug("PERIMETER {} end reason={}", player.getName(), postsTo == CopState.RETURNING ? "OFF" : "CONTACT");
	}

	/** Per AI tick: drops dead or forced-out posts, lets a post that sees the suspect report him, enforces the timeout. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID      id        = player.getUniqueId();
		Perimeter perimeter = active.get(id);
		if (perimeter == null) return;

		if (group == null) {
			active.remove(id);
			return;
		}

		PerimeterSettings settings = settings();
		perimeter.posts.removeIf(cop -> !cop.isValid() || cop.getCurrentState() != CopState.POSTED);

		for (CopNpc cop : List.copyOf(perimeter.posts)) {
			if (!cop.canSee(player, settings.sightRange())) continue;

			group.getSquad().reportSighting(player.getLocation());
			LivingEntity entity = cop.getEntity();
			if (entity != null) {
				copRadio.sayAs(group, cop, "Eyes_On", Map.of("place", copRadio.placeOf(player.getLocation()),
				                                             "direction", copRadio.compassWord(entity.getLocation(),
				                                                                                player.getLocation())));
			}
			cop.releasePost();
			cop.transitionTo(CopState.PURSUING);
			perimeter.posts.remove(cop);
			log.debug("PERIMETER {} post sighted the suspect", player.getName());
		}

		boolean timedOut = clock.getAsLong() - perimeter.startedAt >= settings.maxSeconds() * 1000L;
		if (timedOut || perimeter.posts.isEmpty()) {
			active.remove(id);
			release(perimeter, CopState.PURSUING);
			spent.add(id);
			log.debug("PERIMETER {} end reason={}", player.getName(), timedOut ? "TIMEOUT" : "SIGHTED");
		}
	}

	public boolean isActive(UUID playerId) {
		return active.containsKey(playerId);
	}

	private void release(Perimeter perimeter, CopState postsTo) {
		for (CopNpc cop : perimeter.posts) {
			if (cop.getCurrentState() != CopState.POSTED) continue;
			cop.releasePost();
			cop.transitionTo(postsTo);
		}
	}

	private PerimeterSettings settings() {
		CopConfigProvider config = provider.get();
		return requireNonNullElse(config == null ? null : config.getPerimeterSettings(), PerimeterSettings.DEFAULT);
	}

	/** Valid cops of the group chasing {@code playerId} (PURSUING or COMBAT): configured roles first, then nearest. */
	private List<CopNpc> candidates(CopGroup group, UUID playerId, List<String> roles, Location centre) {
		List<CopNpc> found;
		synchronized (group.getCops()) {
			found = new ArrayList<>(group.getCops());
		}
		found.removeIf(cop -> !cop.isValid() || !Objects.equals(cop.getTargetPlayerId(), playerId)
		                      || (cop.getCurrentState() != CopState.PURSUING && cop.getCurrentState() != CopState.COMBAT));
		found.sort((a, b) -> {
			int byRole = Integer.compare(roleRank(a, roles), roleRank(b, roles));
			return byRole != 0 ? byRole : Double.compare(distance(a, centre), distance(b, centre));
		});
		return found;
	}

	private static int roleRank(CopNpc cop, List<String> roles) {
		CopRole role = cop.getRole();
		if (role == null) return roles.size();
		for (int i = 0; i < roles.size(); i++)
			if (roles.get(i).equalsIgnoreCase(role.name())) return i;
		return roles.size();
	}

	private static double distance(CopNpc cop, Location centre) {
		LivingEntity entity = cop.getEntity();
		if (entity == null) return Double.MAX_VALUE;
		Location at = entity.getLocation();
		return at.getWorld() == centre.getWorld() ? at.distanceSquared(centre) : Double.MAX_VALUE;
	}

	private static final class Perimeter {

		final List<CopNpc> posts;
		final long         startedAt;

		Perimeter(List<CopNpc> posts, long startedAt) {
			this.posts     = posts;
			this.startedAt = startedAt;
		}
	}
}
