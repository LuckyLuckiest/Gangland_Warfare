package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.database.CopSpawnerRepository;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.keystone.npc.entity.EntitySpawner;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

@CustomLog
public class CopSpawnManager extends EntitySpawner<CopSpawner> {

	/** Half-angle, either side of the target->station bearing, of the station side a unit's ring spot prefers. */
	private static final double STATION_SIDE_DEGREES = 60.0;

	private final JavaPlugin         plugin;
	private final CopLoader          copLoader;
	private final NpcMarkManager     markManager;
	private final BartizanNpcWeapons bartizanNpcWeapons;
	private final DownedTargetFilter downedTargetFilter;
	private final DetainmentService  detainmentService;
	private final CuffLockRegistry   cuffLockRegistry;
	private volatile Predicate<UUID> searchGuard = id -> false;

	CopNpcFactory             copNpcFactory; // package-private: tests swap in a mock
	private CopConfigProvider configProvider;
	/** While set, every spot counts as outdoor: the ring takes indoor and outdoor spots alike. */
	private boolean           anyRoof;

	public CopSpawnManager(JavaPlugin plugin, CopLoader copLoader, NpcMarkManager markManager,
	                       BartizanNpcWeapons bartizanNpcWeapons, DownedTargetFilter downedTargetFilter,
	                       IRepository<CopSpawner> repository, DetainmentService detainmentService,
	                       CuffLockRegistry cuffLockRegistry) {
		super(copLoader.getLoadedProvider(), repository);
		this.plugin             = plugin;
		this.copLoader          = copLoader;
		this.markManager        = markManager;
		this.bartizanNpcWeapons = bartizanNpcWeapons;
		this.downedTargetFilter = downedTargetFilter;
		this.detainmentService  = detainmentService;
		this.cuffLockRegistry   = cuffLockRegistry;

		rebuildFactories();
	}

	@Override
	public void onClear() {
		super.onClear();
		copNpcFactory  = null;
		configProvider = null;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		super.onInitialize(firstLoad);
		raiseIdFloor();
		rebuildFactories();
	}

	@Override
	public void reloadSpawners() {
		super.reloadSpawners();
		raiseIdFloor();
	}

	/**
	 * Docket T-180: the loader skips spawners of unloaded worlds, so {@code ID} restarts at the highest LOADED id and the
	 * next spawner would overwrite a stored row. The repository saw every row; the counter never goes below it.
	 */
	// ponytail: instanceof, a mocked IRepository keeps today's floor; a Keystone hook (protected storedMaxId()) if a
	// third spawner needs it
	private void raiseIdFloor() {
		if (repository instanceof CopSpawnerRepository stored) ID = Math.max(ID, stored.getHighestStoredId());
	}

	/** Puts spawner {@code spawnerId} in {@code stationId}'s group ({@code null} = none) and persists it. */
	public void assignStation(int spawnerId, @Nullable Integer stationId) {
		CopSpawner spawner = spawners.get(spawnerId);
		if (spawner == null) return;
		spawner.setStationId(stationId);
		repository.save(spawner);
	}

	/**
	 * Gives {@code station} every UNASSIGNED spawner within {@code radius} blocks of its anchor (horizontal, same world);
	 * a spawner already in another station is skipped.
	 *
	 * @return how many spawners joined
	 */
	public int assignNearby(Station station, double radius) {
		Location anchor = station.getLocation();
		if (anchor == null) return 0;

		int joined = 0;
		for (CopSpawner spawner : new ArrayList<>(spawners.values())) {
			Location at = spawner.getLocation();
			if (spawner.getStationId() != null || at == null || !Objects.equals(at.getWorld(), anchor.getWorld()))
				continue;
			double dx = at.getX() - anchor.getX();
			double dz = at.getZ() - anchor.getZ();
			if (dx * dx + dz * dz > radius * radius) continue;

			assignStation(spawner.getId(), station.getId());
			joined++;
		}
		return joined;
	}

	/** Frees every spawner of {@code stationId} (a station was removed). */
	public void unassignStation(int stationId) {
		for (CopSpawner spawner : spawnersOf(stationId)) assignStation(spawner.getId(), null);
	}

	/** The spawners that belong to {@code stationId}. */
	public List<CopSpawner> spawnersOf(int stationId) {
		List<CopSpawner> result = new ArrayList<>();
		for (CopSpawner spawner : spawners.values())
			if (Objects.equals(spawner.getStationId(), stationId)) result.add(spawner);
		return result;
	}

	/**
	 * Spawns a cop NPC near the given player with the specified tier: at the closest registered spawner whose location
	 * {@code allowed} accepts, else somewhere on the ring around him.
	 *
	 * @param target the player to spawn near
	 * @param tier the cop tier
	 * @param allowed which spawner locations may be used (a recycled cop's spawner is skipped for a while)
	 * @param role the cop's squad role ({@link CopRole#nextRole}); {@code null} spawns the plain tier
	 *
	 * @return the spawned CopNpc, or null if no valid location was found
	 */
	@Nullable
	public CopNpc spawnNearPlayer(Player target, int tier, Predicate<Location> allowed, @Nullable CopRole role) {
		Location spawnLoc = findClosestSpawnerLocation(target, allowed);

		if (spawnLoc != null) {
			return copNpcFactory.createCop(spawnLoc, tier, false, role);
		}

		spawnLoc = findRingLocation(target);

		if (spawnLoc == null) return null;

		return copNpcFactory.createCop(spawnLoc, tier, true, role);
	}

	/**
	 * A spot on the ring around {@code target}. The ring only takes spots as indoor or outdoor as the suspect, so a
	 * suspect under a roof with nothing but open street around him got none, and no cop ever came; for him the ring
	 * is searched again taking either kind (still at his level, within {@code Spawn.Max_Y_Diff}).
	 */
	@Nullable
	Location findRingLocation(Player target) {
		Location spot = findSpawnLocation(target);
		if (spot != null || isOutdoor(target.getLocation())) return spot;

		anyRoof = true;
		try {
			return findSpawnLocation(target);
		} finally {
			anyRoof = false;
		}
	}

	/**
	 * Spawns one due unit: (1) its station's spawners within {@code Spawner_Preference_Radius} of {@code target},
	 * allowed and {@link #isOutOfSight out of sight}, nearest first; (2) {@link #hiddenRing} ahead of the unit's bias,
	 * else on the station side (within 60 degrees of the target->station bearing); (3) {@link #hiddenRing} anywhere;
	 * (4) {@link #spawnNearPlayer}. {@code null} when all fail (the caller requeues the unit).
	 */
	@Nullable
	public CopNpc spawnUnit(Player target, PendingUnit unit, Predicate<Location> allowed) {
		Location at      = target.getLocation();
		Station  station = unit.station();
		boolean  hidden  = true;
		boolean  ring    = true;
		Location spot    = station == null ? null : hiddenStationSpawner(target, station.getId(), allowed);
		if (spot != null) {
			ring = false;
		} else {
			SpawnBias side = unit.bias() != null ? unit.bias() : stationSide(at, station);
			if (side != null) spot = hiddenRing(target, allowed.and(loc -> side.ahead(at, loc)));
			if (spot == null) spot = hiddenRing(target, allowed);
		}

		CopNpc cop;
		if (spot != null) {
			cop = copNpcFactory.createCop(spot, unit.tier(), ring, unit.role());
		} else {
			hidden = false;
			cop    = spawnNearPlayer(target, unit.tier(), allowed, unit.role());
			spot   = cop == null ? null : cop.getSpawnLocation();
		}

		if (cop != null && log.isDebugEnabled()) {
			String ahead = unit.bias() == null || spot == null ? "-" : String.valueOf(unit.bias().ahead(at, spot));
			log.debug("UNIT {} callsign={} tier={} role={} fromStation={} hidden={} bias={} ahead={} at={}",
			          target.getName(), cop.getNpc() == null ? "?" : CopRadio.callsign(cop), unit.tier(),
			          unit.role() == null ? "-" : unit.role().name(), unit.fromStation(), hidden, unit.bias() != null,
			          ahead, spot == null ? "?" : spot.getBlockX() + "," + spot.getBlockY() + "," + spot.getBlockZ());
		}
		return cop;
	}

	/**
	 * The nearest spawner of station {@code stationId} within {@code Spawner_Preference_Radius} of {@code target}
	 * (and {@code Spawner_Max_Y_Diff}, as {@link #findClosestSpawnerLocation}) that {@code allowed} accepts and that is
	 * {@link #isOutOfSight out of sight}; {@code null} when none.
	 */
	private @Nullable Location hiddenStationSpawner(Player target, int stationId, Predicate<Location> allowed) {
		Location at     = target.getLocation();
		double   radius = configProvider.getSpawnerPreferenceRadius();
		List<Location> candidates = new ArrayList<>();
		for (CopSpawner spawner : spawnersOf(stationId)) {
			Location loc = spawner.getLocation();
			if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(at.getWorld())) continue;
			if (Math.abs(loc.getY() - at.getY()) > configProvider.getSpawnerMaxYDiff()) continue;
			if (horizontalSq(loc, at) > radius * radius || !allowed.test(loc)) continue;
			candidates.add(loc);
		}
		candidates.sort(Comparator.comparingDouble(loc -> horizontalSq(loc, at)));
		for (Location loc : candidates)
			if (isOutOfSight(loc, target)) return loc;
		return null;
	}

	/**
	 * The station side of {@code at} as a bias cone: within {@link #STATION_SIDE_DEGREES} of the bearing from the
	 * target to the station's anchor; {@code null} without a station.
	 */
	private static @Nullable SpawnBias stationSide(Location at, @Nullable Station station) {
		if (station == null) return null;
		Vector bearing = new Vector(station.getX() - at.getX(), 0, station.getZ() - at.getZ());
		return new SpawnBias(bearing, at, Long.MAX_VALUE, STATION_SIDE_DEGREES);
	}

	private static double horizontalSq(Location a, Location b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * {@link #findHiddenSpawnLocation} wrapped like {@link #findRingLocation}: a suspect indoors whose first pass finds
	 * nothing is searched again taking indoor and outdoor spots alike.
	 */
	@Nullable
	Location hiddenRing(Player target, Predicate<Location> allowed) {
		Location spot = findHiddenSpawnLocation(target, allowed);
		if (spot != null || isOutdoor(target.getLocation())) return spot;

		anyRoof = true;
		try {
			return findHiddenSpawnLocation(target, allowed);
		} finally {
			anyRoof = false;
		}
	}

	// ponytail: leans on EntitySpawner judging both the suspect and each ring spot through isOutdoor; a Keystone ring
	// option (match indoor/outdoor or not) replaces this if that ever changes.
	@Override
	protected boolean isOutdoor(Location location) {
		return anyRoof || super.isOutdoor(location);
	}

	/**
	 * Spawns a cop NPC at a specific configured spawn location.
	 *
	 * @param location the spawn location
	 * @param tier the cop tier
	 *
	 * @return the spawned CopNpc, or null on failure
	 */
	@Nullable
	public CopNpc spawnAtLocation(Location location, int tier) {
		return copNpcFactory.createCop(location, tier);
	}

	/**
	 * Returns the number of cops that should be active for a given wanted level.
	 *
	 * @param wantedLevel the player's wanted level
	 *
	 * @return the target cop count
	 */
	public int getTargetCopCount(int wantedLevel) {
		return configProvider.getCopsPerWantedLevel()
		                     .getOrDefault(wantedLevel,
		                                   Math.min(wantedLevel + 1, configProvider.getMaxCopsPerPlayer()));
	}

	/**
	 * Determines the cop tier that should be spawned for a given wanted level.
	 *
	 * @param wantedLevel the player's wanted level
	 *
	 * @return the tier number
	 */
	public int getTierForWantedLevel(int wantedLevel) {
		return Math.min(wantedLevel, configProvider.getMaxTier());
	}

	@Override
	protected CopSpawner createSpawnerPoint(int id, Location location) {
		return new CopSpawner(id, location);
	}

	/**
	 * Tells the cops which players are on the post-escape search (0.16.1 T-187). Read per cop tick, so it may be set after
	 * construction; the default says nobody is.
	 */
	public void setSearchGuard(Predicate<UUID> searchGuard) {
		this.searchGuard = searchGuard;
	}

	private void rebuildFactories() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		this.configProvider = provider;

		CopBehaviorFactory behaviorFactory = new CopBehaviorFactory(provider, () -> this, detainmentService,
		                                                            cuffLockRegistry, id -> searchGuard.test(id));
		this.copNpcFactory = new CopNpcFactory(plugin, provider, behaviorFactory, markManager, bartizanNpcWeapons,
		                                       downedTargetFilter);
	}
}
