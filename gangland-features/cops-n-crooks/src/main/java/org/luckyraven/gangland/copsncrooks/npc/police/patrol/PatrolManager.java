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
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
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
	private final Map<Integer, String>                         names      = new LinkedHashMap<>();
	/** When each station last lost a beat cop (radio-clock ms): its replacement waits out {@code Respawn_Seconds}. */
	private final Map<Integer, Long>                           lostAt     = new LinkedHashMap<>();
	private       Function<UUID, CopGroup>                     groupLookup = id -> null;
	/** Stations whose lost beat cop is a silence dispatch has not noticed yet: no replacement (0.21.2, T-545). */
	private       IntPredicate                                 stationHeld = id -> false;
	/** A station's replacement owed to an Officer_Missing ({@link #respondTo}): where it walks and holds, and until when. */
	private final Map<Integer, Response>                       owed       = new LinkedHashMap<>();

	/** The spot an owed replacement walks to and the end of its hold there (radio-clock ms). */
	private record Response(Location at, long until) {
	}

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
		owed.values().removeIf(response -> now >= response.until());
		for (Station station : stations.all()) {
			Location anchor = station.getLocation();
			if (anchor == null || anchor.getWorld() == null) {
				continue;
			}
			int id = station.getId();
			live.add(id);
			names.put(id, station.getName());
			double nearest = nearestPlayer(anchor);
			List<CopNpc> cops = beats.computeIfAbsent(id, key -> new ArrayList<>());
			boolean hadCops = !cops.isEmpty();
			cops.removeIf(cop -> {
				if (cop.isValid()) {
					return false;
				}
				destroy(cop);
				lostAt.put(id, now);
				return true;
			});
			if (hadCops && cops.isEmpty()) {
				logZeroCops(id);
			}
			Long lost = lostAt.get(id);
			// cause 6: a lost beat cop is not replaced the next second in front of whoever took it down
			// 0.21.2 (T-545): nor while his death is a silence: dispatch does not know he is gone (respondTo sends it)
			boolean respawnReady = (lost == null || now - lost >= s.respawnSeconds() * 1000L) && !stationHeld.test(id);
			if (nearest <= s.activationRadius()) {
				emptySince.remove(id);
				// owner ruling R6 (0.21.2 R2, T-587): never at a station spot a player has in view; owner ruling T-606
				// (R3): a watched station is staffed from a nearby spot nobody has in view, its cops walking in; with no
				// such spot this run the next run tries again
				while (respawnReady && cops.size() < s.copsPerStation()) {
					Location at      = anchor;
					boolean  offsite = spawner.watched(anchor);
					if (offsite) {
						at = spawner.unwatchedNear(anchor);
						if (at == null) {
							log.debug("PATROL station={} held=watched", station.getName());
							break;
						}
					}
					CopNpc cop = spawner.spawnAtLocation(at.clone(), s.tier());
					if (cop == null) {
						break;
					}
					cop.transitionTo(CopState.PATROL);
					cops.add(cop);
					log.debug("PATROL station={} cops={}", station.getName(), cops.size());
					if (offsite) {
						cop.setBeatAnchor(anchor.clone());
						log.debug("PATROL station={} spawn=offsite at={},{},{}", station.getName(), at.getBlockX(),
						          at.getBlockY(), at.getBlockZ());
					}
					Response response = owed.remove(id);
					if (response != null) {
						investigate(cop, response.at(), response.until());
					} else if (offsite) {
						cop.navigateTo(anchor.clone());
					}
				}
			}
			if (nearest <= s.despawnRadius()) {
				emptySince.remove(id);
			} else if (!cops.isEmpty()) {
				Long since = emptySince.putIfAbsent(id, now);
				if (since != null && now - since >= s.despawnAfterSeconds() * 1000L) {
					cops.forEach(this::destroy);
					cops.clear();
					log.debug("PATROL station={} cops=0", station.getName());
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
			lostAt.remove(entry.getKey());
			owed.remove(entry.getKey());
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

	/** T-322: a beat whose last cop was pruned as invalid reports it, like the timer despawn does. */
	private void logZeroCops(int id) {
		log.debug("PATROL station={} cops=0", names.get(id));
		emptySince.remove(id);
	}

	/** Where a beat cop finds its target's squad (for the cease-fire); default none. */
	public void setGroupLookup(Function<UUID, CopGroup> lookup) {
		this.groupLookup = lookup;
	}

	/** Which stations hold their replacement back for an open silence ({@code CopCrimeReports.silentStation}). */
	public void setStationHold(IntPredicate stationHeld) {
		this.stationHeld = stationHeld;
	}

	/** The station whose beat {@code cop} walks, empty for a cop of no beat. */
	public OptionalInt stationOf(CopNpc cop) {
		for (Map.Entry<Integer, List<CopNpc>> beat : beats.entrySet()) {
			if (beat.getValue().contains(cop)) return OptionalInt.of(beat.getKey());
		}
		return OptionalInt.empty();
	}

	/**
	 * Officer_Missing for a beat cop of {@code stationId} (0.21.2, T-545): the nearest live beat cop of the station walks
	 * to {@code at}, his last check-in, now, and the replacement his silence held back follows once {@link #manage} may
	 * spawn it: {@code Respawn_Seconds} after the loss (T-404) and on a run no player has the station spot in view (R6,
	 * R2 T-587). Both hold the spot for {@code holdMillis} from now (the scene's {@code Scene_Seconds}, T-579):
	 * {@code PatrolBehavior} neither wanders nor walks back before then. A replacement still unspawned by then stays on
	 * its beat. Two units in all; fewer when the station has no one else.
	 *
	 * @return the units sent, the owed replacement included
	 */
	public int respondTo(int stationId, Location at, long holdMillis) {
		Station station = null;
		for (Station candidate : stations.all()) {
			if (candidate.getId() == stationId) station = candidate;
		}
		List<CopNpc> cops   = beats.get(stationId);
		Location     anchor = station == null ? null : station.getLocation();
		if (cops == null || anchor == null || anchor.getWorld() == null) return 0;

		long until = clock.getAsLong() + holdMillis;
		int  owing = 0;
		if (cops.size() < settings.get().copsPerStation()) {
			owed.put(stationId, new Response(at.clone(), until));
			owing = 1;
		}
		CopNpc nearest = null;
		double best    = Double.MAX_VALUE;
		for (CopNpc cop : cops) {
			LivingEntity body  = cop.getEntity();
			Location     there = body == null ? null : body.getLocation();
			if (!cop.isValid() || there == null || there.getWorld() != at.getWorld()) continue;
			double distance = there.distanceSquared(at);
			if (distance < best) {
				best    = distance;
				nearest = cop;
			}
		}
		if (nearest != null) investigate(nearest, at, until);
		int sent = owing + (nearest != null ? 1 : 0);
		log.debug("PATROL station={} respond={} at={},{},{}", names.getOrDefault(stationId, station.getName()), sent,
		          at.getBlockX(), at.getBlockY(), at.getBlockZ());
		return sent;
	}

	private static void investigate(CopNpc cop, Location at, long until) {
		cop.setInvestigateUntil(until);
		cop.navigateTo(at);
	}

	/** One AI tick for every live beat cop (called every 10 ticks): resolves each cop's target and runs its behaviour. */
	public void aiTick() {
		for (CopNpc cop : beatCops()) {
			if (!cop.isValid()) {
				destroy(cop);
				beats.forEach((id, list) -> {
					if (!list.remove(cop)) {
						return;
					}
					lostAt.put(id, clock.getAsLong());
					if (list.isEmpty()) {
						logZeroCops(id);
					}
				});
				continue;
			}
			UUID   id     = cop.getTargetPlayerId();
			Player target = id == null ? null : Bukkit.getPlayer(id);
			boolean live  = target != null && target.isOnline() && !target.isDead();
			CopGroup squad = id == null ? null : groupLookup.apply(id);
			if (squad != null && squad.isCeaseFire()) {
				live = false;
			}
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
		lostAt.clear();
		owed.clear();
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
