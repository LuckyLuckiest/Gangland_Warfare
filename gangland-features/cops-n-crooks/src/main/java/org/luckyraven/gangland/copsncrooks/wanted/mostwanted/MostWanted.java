package org.luckyraven.gangland.copsncrooks.wanted.mostwanted;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.MostWantedSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The city's Most Wanted: the one online player with the most crime-earned stars (four or more, held for a while). Main
 * thread only.
 *
 * @since 0.21.0
 */
@CustomLog
public class MostWanted implements BeanLifecycle {

	private final JavaPlugin                                  plugin;
	private final ChaseArcs                                   arcs;
	private final ChaseConfigLoader                           config;
	private final WantedMessages                              messages;
	private final Supplier<Collection<? extends Player>>      online;
	private final Consumer<String>                            broadcast;
	private final BiConsumer<Player, Integer>                 radio;

	private final Map<UUID, Long> qualifiedSince = new HashMap<>();
	private BukkitTask            task;
	private UUID                  holder;
	private volatile String       holderName;
	private volatile int          holderStars;
	private boolean               broadcastOnce;
	private long                  lastBroadcastAt;

	public MostWanted(JavaPlugin plugin, ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages,
	                  Supplier<Collection<? extends Player>> online, Consumer<String> broadcast,
	                  BiConsumer<Player, Integer> radio) {
		this.plugin    = plugin;
		this.arcs      = arcs;
		this.config    = config;
		this.messages  = messages;
		this.online    = online;
		this.broadcast = broadcast;
		this.radio     = radio;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (task == null) {
			task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
		}
	}

	@Override
	public void onClear() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	/** The production beat: {@code tick(arcs.now())}. */
	public void tick() {
		tick(arcs.now());
	}

	void tick(long now) {
		MostWantedSettings s = config.getMostWanted();
		if (!s.enabled()) {
			clearHolder("LOST");
			qualifiedSince.clear();
			return;
		}

		Map<UUID, ChaseArc> seen   = new HashMap<>();
		Map<UUID, Player>   names  = new HashMap<>();
		for (Player p : online.get()) {
			if (NpcSupport.isNpc(p)) {
				continue;
			}
			UUID     id  = p.getUniqueId();
			ChaseArc arc = arcs.arc(id);
			if (arc == null || arc.offlineAt() != 0) {
				continue;
			}
			seen.put(id, arc);
			names.put(id, p);
			if (arc.earned() >= s.minStars()) {
				qualifiedSince.putIfAbsent(id, now);
			} else {
				qualifiedSince.remove(id);
			}
		}
		qualifiedSince.keySet().removeIf(id -> !seen.containsKey(id) || seen.get(id).earned() < s.minStars());

		if (holder != null) {
			ChaseArc h = seen.get(holder);
			if (h == null || h.earned() < s.minStars()) {
				clearHolder("LOST");
			} else {
				holderStars = h.earned();
			}
		}

		UUID best = null;
		for (Map.Entry<UUID, Long> e : qualifiedSince.entrySet()) {
			if (now - e.getValue() >= s.holdSeconds() * 1000L && (best == null || beats(e.getKey(), best, seen))) {
				best = e.getKey();
			}
		}
		if (best == null || best.equals(holder)) {
			return;
		}
		int earned = seen.get(best).earned();
		if (holder != null && earned <= holderStars) {
			return;
		}

		Player p = names.get(best);
		holder      = best;
		holderName  = p.getName();
		holderStars = earned;
		boolean announce = !broadcastOnce || now - lastBroadcastAt >= s.cooldownSeconds() * 1000L;
		if (announce) {
			broadcastOnce   = true;
			lastBroadcastAt = now;
			broadcast.accept(messages.format(WantedMessages.Key.MOST_WANTED_NEW, Map.of("player", holderName, "stars",
			                                                                           Wanted.buildStars(earned, 5))));
			radio.accept(p, earned);
		}
		log.debug("MOST_WANTED {} stars={} announced={}", holderName, earned, announce);
	}

	// Ranking: most earned, then the sitting holder, then earliest earnedAt, then lower UUID.
	private boolean beats(UUID a, UUID b, Map<UUID, ChaseArc> seen) {
		ChaseArc x = seen.get(a);
		ChaseArc y = seen.get(b);
		if (x.earned() != y.earned()) {
			return x.earned() > y.earned();
		}
		if (a.equals(holder) != b.equals(holder)) {
			return a.equals(holder);
		}
		if (x.earnedAt() != y.earnedAt()) {
			return x.earnedAt() < y.earnedAt();
		}
		return a.compareTo(b) < 0;
	}

	private void clearHolder(String reason) {
		if (holder != null) {
			log.debug("MOST_WANTED none reason={}", reason);
		}
		holder      = null;
		holderName  = null;
		holderStars = 0;
	}

	/** A player left: his qualifying clock goes, and the title with it. */
	public void quit(UUID id) {
		qualifiedSince.remove(id);
		if (id.equals(holder)) {
			clearHolder("QUIT");
		}
	}

	public @Nullable String holderName() {
		return holderName;
	}

	/** The holder's earned stars; 0 when the title is vacant. */
	public int holderStars() {
		return holderStars;
	}
}
