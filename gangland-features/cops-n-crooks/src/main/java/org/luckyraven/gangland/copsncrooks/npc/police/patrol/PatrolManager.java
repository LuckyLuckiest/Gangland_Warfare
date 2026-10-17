package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The beat cops (0.20.0 lane patrol): groupless tier cops spawned at a station anchor while a player is near, driven
 * by their own repeating main-thread task, despawned when nobody is near (and on shutdown/reload). They belong to no
 * {@code CopGroup}; {@code CopManager.addCopResolver(this::beatCopOf)} makes the cop listeners treat them as cops.
 * Spawned only through {@link CopSpawnManager#spawnAtLocation} (the factory sets the POLICE mark and SHOULD_SAVE false).
 */
@CustomLog
public class PatrolManager implements BeanLifecycle {

	private final JavaPlugin                                   plugin;
	private final CopPerception                                perception;
	private       BukkitTask                                   task;
	private final Supplier<PatrolSettings>                     settings;
	private final StationRegistry                              stations;
	private final CopSpawnManager                              spawner;
	private final NpcMarkManager                               markManager;
	private final Supplier<Collection<? extends Player>>       players;
	private final Predicate<Entity>                            isNpc;
	private final BooleanSupplier                              citizensAvailable;
	private final LongSupplier                                 clock;
	private final Map<Integer, List<CopNpc>>                   beats      = new LinkedHashMap<>();
	private final Map<Integer, Long>                           emptySince = new LinkedHashMap<>();

	/**
	 * @param players the players that count as "near"
	 * @param isNpc Citizens NPCs never count
	 * @param citizensAvailable whether Citizens is present (absent: no beats)
	 * @param clock milliseconds
	 */
	public PatrolManager(JavaPlugin plugin, Supplier<PatrolSettings> settings, StationRegistry stations,
	                     CopSpawnManager spawner, NpcMarkManager markManager, CopPerception perception,
	                     Supplier<Collection<? extends Player>> players, Predicate<Entity> isNpc,
	                     BooleanSupplier citizensAvailable, LongSupplier clock) {
		this.plugin            = plugin;
		this.perception        = perception;
		this.settings          = settings;
		this.stations          = stations;
		this.spawner           = spawner;
		this.markManager       = markManager;
		this.players           = players;
		this.isNpc             = isNpc;
		this.citizensAvailable = citizensAvailable;
		this.clock             = clock;
	}

	/** Spawn / keep / despawn the beat cops of every station (called every 20 ticks). */
	public void manage() {
		PatrolSettings s = settings.get();
		if (!s.enabled() || !citizensAvailable.getAsBoolean()) {
			despawnAll();
			return;
		}
		long        now  = clock.getAsLong();
		Set<Integer> live = new HashSet<>();
		for (Station station : stations.all()) {
			Location anchor = station.getLocation();
			if (anchor == null || anchor.getWorld() == null) {
				continue;
			}
			int id = station.getId();
			live.add(id);
			double nearest = nearestPlayer(anchor);
			List<CopNpc> cops = beats.computeIfAbsent(id, key -> new ArrayList<>());
			cops.removeIf(cop -> {
				if (cop.isValid()) {
					return false;
				}
				destroy(cop);
				return true;
			});
			if (nearest <= s.activationRadius()) {
				emptySince.remove(id);
				while (cops.size() < s.copsPerStation()) {
					CopNpc cop = spawner.spawnAtLocation(anchor.clone(), s.tier());
					if (cop == null) {
						break;
					}
					cop.transitionTo(CopState.PATROL);
					cops.add(cop);
					log.debug("PATROL station={} cops={}", station.getName(), cops.size());
				}
			}
			if (nearest <= s.despawnRadius()) {
				emptySince.remove(id);
			} else if (!cops.isEmpty()) {
				Long since = emptySince.putIfAbsent(id, now);
				if (since != null && now - since >= s.despawnAfterSeconds() * 1000L) {
					cops.forEach(this::destroy);
					cops.clear();
					emptySince.remove(id);
				}
			}
		}
		beats.entrySet().removeIf(entry -> {
			if (live.contains(entry.getKey())) {
				return false;
			}
			entry.getValue().forEach(this::destroy);
			emptySince.remove(entry.getKey());
			return true;
		});
	}

	private double nearestPlayer(Location anchor) {
		double nearest = Double.MAX_VALUE;
		for (Player player : players.get()) {
			if (isNpc.test(player) || !player.isOnline() || !anchor.getWorld().equals(player.getWorld())) {
				continue;
			}
			nearest = Math.min(nearest, anchor.distance(player.getLocation()));
		}
		return nearest;
	}

	private void destroy(CopNpc cop) {
		cop.destroy(entity -> markManager.removeMark(entity));
	}

	/** One AI tick for every live beat cop (called every 10 ticks): resolves each cop's target and runs its behaviour. */
	public void aiTick() {
		for (CopNpc cop : beatCops()) {
			if (!cop.isValid()) {
				destroy(cop);
				beats.values().forEach(list -> list.remove(cop));
				continue;
			}
			UUID   id     = cop.getTargetPlayerId();
			Player target = id == null ? null : Bukkit.getPlayer(id);
			boolean live  = target != null && target.isOnline() && !target.isDead();
			CopState state = cop.getCurrentState();
			if (state == CopState.COMBAT && live) {
				cop.tick(target);
			} else if (state == CopState.WARNING) {
				cop.tick(live ? target : null);
			} else if (state == CopState.PATROL) {
				cop.tick(null);
			} else {
				cop.setCombatForced(false);
				if (state == CopState.COMBAT) {
					cop.setTargetPlayerId(null);
				}
				cop.transitionTo(CopState.PATROL);
				cop.tick(null);
			}
		}
	}

	/** Every live beat cop, all stations. */
	public List<CopNpc> beatCops() {
		List<CopNpc> all = new ArrayList<>();
		beats.values().forEach(all::addAll);
		return all;
	}

	/** The beat cop whose body is {@code entity}, or {@code null}: the {@code CopManager.addCopResolver} function. */
	public @Nullable CopNpc beatCopOf(@Nullable Entity entity) {
		if (entity == null) {
			return null;
		}
		for (CopNpc cop : beatCops()) {
			Entity body = cop.getEntity();
			if (body != null && (body == entity || body.getUniqueId().equals(entity.getUniqueId()))) {
				return cop;
			}
		}
		return null;
	}

	/** Whether some live beat cop within {@code range} of {@code player} acquires them (darkness and disguise apply). */
	public boolean beatCopSees(Player player, double range) {
		for (CopNpc cop : beatCops()) {
			if (cop.isValid() && cop.distanceTo(player) <= range && perception.acquires(cop, player, range)) {
				return true;
			}
		}
		return false;
	}

	/** Despawns every beat cop through {@code markManager} and forgets them. */
	public void despawnAll() {
		beats.values().forEach(cops -> cops.forEach(this::destroy));
		beats.clear();
		emptySince.clear();
	}

	/** Starts the repeating main-thread task. */
	@Override
	public void onInitialize(boolean firstLoad) {
		cancelTask();
		int[] runs = {0};
		task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			aiTick();
			if (++runs[0] % 2 == 0) {
				manage();
			}
		}, 20L, 10L);
	}

	private void cancelTask() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	/** Stops the task and despawns every beat cop (never leave Citizens NPCs behind). */
	@Override
	public void onClear() {
		cancelTask();
		despawnAll();
	}

	@Override
	public void onShutdown() {
		cancelTask();
		despawnAll();
	}
}
