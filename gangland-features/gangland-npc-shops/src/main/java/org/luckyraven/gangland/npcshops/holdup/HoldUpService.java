package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.crime.Crimes;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Polls (every 5 ticks, main thread) for players aiming a Bartizan weapon at a trader and runs the fill / alarm /
 * payout sessions. TDD skeleton: behaviour lands in the green wave. {@code plugin} may be null (no scheduling).
 */
public final class HoldUpService implements BeanLifecycle {

	private final JavaPlugin                             plugin;
	private final Supplier<HoldUpSettings>               loader;
	private final Supplier<Collection<? extends Player>> players;
	private final Predicate<Player>                      weaponHeld;
	private final BiFunction<Player, HoldUpSettings, HoldUpTarget> aimResolver;
	private final MoodService                            mood;
	private final CrimeService                           crimes;
	private final HoldUpPayout                           payout;
	private final LongSupplier                           clockMillis;
	private final BiConsumer<Player, String>             actionBar;
	private final Consumer<String>                       debug;

	public HoldUpService(JavaPlugin plugin, Supplier<HoldUpSettings> loader,
	                     Supplier<Collection<? extends Player>> players, Predicate<Player> weaponHeld,
	                     BiFunction<Player, HoldUpSettings, HoldUpTarget> aimResolver, MoodService mood, CrimeService crimes,
	                     HoldUpPayout payout, LongSupplier clockMillis, BiConsumer<Player, String> actionBar,
	                     Consumer<String> debug) {
		this.plugin      = plugin;
		this.loader      = loader;
		this.players     = players;
		this.weaponHeld  = weaponHeld;
		this.aimResolver = aimResolver;
		this.mood        = mood;
		this.crimes      = crimes;
		this.payout      = payout;
		this.clockMillis = clockMillis;
		this.actionBar   = actionBar;
		this.debug       = debug;
		this.cached      = loader.get();
	}

	private static final class Session {

		final UUID     traderId;
		final String   name;
		final Location location;
		final long     startMillis;
		boolean        alarmed;

		Session(HoldUpTarget target, long startMillis) {
			this.traderId    = target.traderId();
			this.name        = target.name();
			this.location    = target.location();
			this.startMillis = startMillis;
		}

	}

	private final Map<UUID, Session> sessions      = new HashMap<>();
	private final Map<UUID, Long>    aimSince      = new HashMap<>();
	private final Map<UUID, Long>    lastAimed     = new HashMap<>();
	private final Map<UUID, Long>    cooldownUntil = new HashMap<>();
	private final Set<UUID>          closedShown   = new HashSet<>();

	private HoldUpSettings cached;
	private BukkitTask     task;

	/** One poll. Called every 5 ticks by the repeating task. */
	public void tick() {
		if (cached == null) {
			cached = loader.get();
		}
		HoldUpSettings s = cached;
		if (!s.enable()) {
			return;
		}
		long      now  = clockMillis.getAsLong();
		Set<UUID> seen = new HashSet<>();
		for (Player p : players.get()) {
			seen.add(p.getUniqueId());
			if (!p.isOnline()) {
				forget(p.getUniqueId());
				continue;
			}
			handle(p, s, now);
		}
		sessions.keySet().removeIf(id -> !seen.contains(id));
		aimSince.keySet().removeIf(id -> !seen.contains(id));
		lastAimed.keySet().removeIf(id -> !seen.contains(id));
		closedShown.removeIf(id -> !seen.contains(id));
	}

	private void forget(UUID id) {
		sessions.remove(id);
		aimSince.remove(id);
		lastAimed.remove(id);
		closedShown.remove(id);
	}

	private void handle(Player p, HoldUpSettings s, long now) {
		if (!weaponHeld.test(p)) {
			miss(p, s, now);
			return;
		}
		HoldUpTarget target = aimResolver.apply(p, s);
		if (target == null) {
			miss(p, s, now);
			return;
		}
		UUID    id      = p.getUniqueId();
		Session session = sessions.get(id);
		lastAimed.put(id, now);
		if (session != null && !session.traderId.equals(target.traderId())) {
			abort(p, s, session);
			aimSince.remove(id);
			session = null;
		}
		if (session != null) {
			advance(p, session, s, now);
			return;
		}
		Long until = cooldownUntil.get(target.traderId());
		if (until != null && until > now) {
			if (closedShown.add(id)) {
				p.sendMessage(s.closed());
			}
			return;
		}
		long since = aimSince.computeIfAbsent(id, k -> now);
		if (now - since >= 1000L) {
			sessions.put(id, new Session(target, now));
			debug.accept("HOLDUP " + p.getName() + " trader=" + target.name() + " state=start");
		}
	}

	private void miss(Player p, HoldUpSettings s, long now) {
		UUID    id      = p.getUniqueId();
		Session session = sessions.get(id);
		if (session != null) {
			Long last = lastAimed.get(id);
			if (last != null && now - last < 1000L) {
				return;
			}
			abort(p, s, session);
		}
		aimSince.remove(id);
		closedShown.remove(id);
	}

	private void abort(Player p, HoldUpSettings s, Session session) {
		sessions.remove(p.getUniqueId());
		p.sendMessage(s.aborted());
		debug.accept("HOLDUP " + p.getName() + " trader=" + session.name + " state=abort");
	}

	private void advance(Player p, Session session, HoldUpSettings s, long now) {
		double elapsed = (now - session.startMillis) / 1000.0;
		double alarmAt = mood.getMood(session.traderId, p.getUniqueId()) <= s.dislikeMood() ? s.alarmSecondsDisliked()
		                                                                                      : s.alarmSeconds();
		if (!session.alarmed && elapsed >= alarmAt) {
			trip(p, session, s, now);
		}
		if (elapsed >= s.fillSeconds()) {
			BigDecimal take = payout.pay(p);
			p.sendMessage(s.done().replace("{take}", take.toPlainString()));
			debug.accept("HOLDUP " + p.getName() + " trader=" + session.name + " state=done take="
			             + take.toPlainString());
			if (!session.alarmed) {
				trip(p, session, s, now);
			}
			UUID id = p.getUniqueId();
			sessions.remove(id);
			aimSince.remove(id);
			cooldownUntil.put(session.traderId, now + s.cooldownSeconds() * 1000L);
			return;
		}
		int remaining = (int) Math.ceil(s.fillSeconds() - elapsed);
		actionBar.accept(p, s.filling().replace("{seconds}", String.valueOf(remaining)));
	}

	private void trip(Player p, Session session, HoldUpSettings s, long now) {
		crimes.commit(p, Crimes.STORE_ROBBERY, session.location, false, 1);
		session.alarmed = true;
		cooldownUntil.put(session.traderId, now + s.cooldownSeconds() * 1000L);
		debug.accept("HOLDUP " + p.getName() + " trader=" + session.name + " state=alarm");
	}

	/** Re-reads the settings through the loader (managed reload). */
	public void reload() {
		cached = loader.get();
	}

	/** Drops every session and cooldown (shutdown / reload). */
	public void clear() {
		sessions.clear();
		aimSince.clear();
		lastAimed.clear();
		cooldownUntil.clear();
		closedShown.clear();
	}

	private void cancelTask() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	@Override
	public void onPreClear() {
		cancelTask();
	}

	@Override
	public void onClear() {
		clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) {
			reload();
		}
		if (plugin != null && task == null) {
			task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
		}
	}

	@Override
	public void onShutdown() {
		cancelTask();
		clear();
	}

	public boolean hasSession(UUID playerId) {
		return sessions.containsKey(playerId);
	}

}
