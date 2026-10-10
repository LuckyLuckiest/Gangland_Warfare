package org.luckyraven.gangland.turf.placeholder;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.map.TurfHudLine;
import org.luckyraven.gangland.turf.task.TurfLocationTracker;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Answers the six {@code turf-} placeholders (%gangland_turf-name% and the rest) for an online player. The current turf
 * is read O(1) from the location tracker's cache; the nearest turf in the player's world is computed at most once per
 * second per player. Names are stripped of colour so the scoreboard config decides the colours. Outside a turf the
 * answer is the Hud.Placeholder_None dash. Offline players and tokens this class does not own answer null.
 */
public final class TurfPlaceholderContribution implements PlaceholderContribution {

	/** How long a computed nearest turf is reused for the same player. */
	static final long NEAREST_CACHE_MILLIS = 1000L;

	private static final Set<String> TOKENS = Set.of("turf-name", "turf-owner", "turf-state", "turf-relation",
	                                                 "turf-nearest-name", "turf-nearest-distance");

	private final TurfLocationTracker tracker;
	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final CaptureEligibility  eligibility;
	private final TurfMessages        text;
	private final LongSupplier        clock;
	private final Map<UUID, CachedNearest> nearestCache = new ConcurrentHashMap<>();

	private record CachedNearest(@Nullable TurfGeometry.Nearest nearest, long computedAt) {
	}

	public TurfPlaceholderContribution(TurfLocationTracker tracker,
	                                   TurfManager turfs,
	                                   GangLookupContract gangs,
	                                   UserLookupContract users,
	                                   CaptureEligibility eligibility,
	                                   TurfMessages text,
	                                   LongSupplier clock) {
		this.tracker     = tracker;
		this.turfs       = turfs;
		this.gangs       = gangs;
		this.users       = users;
		this.eligibility = eligibility;
		this.text        = text;
		this.clock       = clock;
	}

	@Override
	public @Nullable String resolve(OfflinePlayer player, String parameter) {
		if (!TOKENS.contains(parameter)) {
			return null;
		}
		Player online = player.getPlayer();
		if (online == null) {
			return null;
		}

		long  now     = clock.getAsLong();
		Turf  current = tracker.getPlayerTurfCache().get(online.getUniqueId());
		return switch (parameter) {
			case "turf-name" -> current == null ? none() : plain(current.getDisplayName());
			case "turf-owner" -> current == null ? none() : ownerWord(current);
			case "turf-state" -> current == null ? none() : stateWord(current, now);
			case "turf-relation" -> current == null ? none() : relation(online, current).name().toLowerCase(Locale.ROOT);
			case "turf-nearest-name" -> {
				TurfGeometry.Nearest nearest = nearest(online, current, now);
				yield nearest == null ? none() : plain(nearest.turf().getDisplayName());
			}
			case "turf-nearest-distance" -> {
				TurfGeometry.Nearest nearest = nearest(online, current, now);
				yield nearest == null ? none() : String.valueOf(nearest.distance());
			}
			default -> null;
		};
	}

	private String none() {
		return plain(text.text("Hud.Placeholder_None", "-"));
	}

	private String ownerWord(Turf turf) {
		if (turf.isUnclaimed()) {
			return "unclaimed";
		}
		return plain(GangDisplayNameResolver.resolve(gangs.findById(turf.getOwnerGangId())));
	}

	private String stateWord(Turf turf, long now) {
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (TurfHudLine.isContesting(state)) {
			return "under attack";
		}
		return eligibility.evaluate(turf, now).attackable() ? "attackable" : "shielded";
	}

	private TurfRelation relation(Player player, Turf turf) {
		User<Player> user = users.findByPlayer(player);
		Integer gangId = user != null && user.hasGang() ? user.getGangId() : null;
		return TurfRelation.of(turf, gangId, gangs);
	}

	/**
	 * The turf the player stands in when there is one (distance 0), else the nearest turf in their world, cached per
	 * player for NEAREST_CACHE_MILLIS. Placeholders may be resolved from the async scoreboard thread; the world,
	 * location and evaluate calls here only read state, which is acceptable.
	 */
	private @Nullable TurfGeometry.Nearest nearest(Player player, @Nullable Turf current, long now) {
		if (current != null) {
			return new TurfGeometry.Nearest(current, 0, "");
		}
		CachedNearest cached = nearestCache.get(player.getUniqueId());
		if (cached != null && now - cached.computedAt() < NEAREST_CACHE_MILLIS) {
			return cached.nearest();
		}

		TurfGeometry.Nearest found = null;
		World world = player.getWorld();
		if (world != null) {
			Location at = player.getLocation();
			found = TurfGeometry.nearest(turfs.getTurfsInWorld(world.getName()), at.getBlockX(), at.getBlockZ());
		}
		// ponytail: sweeps stale entries on each miss (O(online players)); a quit listener would remove them eagerly
		nearestCache.values().removeIf(entry -> now - entry.computedAt() >= NEAREST_CACHE_MILLIS);
		nearestCache.put(player.getUniqueId(), new CachedNearest(found, now));
		return found;
	}

	private static String plain(String value) {
		return ChatColor.stripColor(ChatUtil.color(value));
	}
}
