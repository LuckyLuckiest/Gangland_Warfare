package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.PendingUnit;
import org.luckyraven.gangland.copsncrooks.npc.police.dispatch.SpawnBias;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopSightlines;
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
import java.util.function.Function;
import java.util.function.Predicate;

@CustomLog
public class CopSpawnManager extends EntitySpawner<CopSpawner> {

	/** Half-angle, either side of the target->station bearing, of the station side a unit's ring spot prefers. */
	private static final double STATION_SIDE_DEGREES = 60.0;
	/**
	 * A spawn spot stays within this share of {@code Cops.Pursuit.Max_Distance} (3D) of the suspect: a cop spawned at
	 * the leash line walks home in the second it spawns and is re-dispatched (0.21.1 cause 2).
	 */
	public static final  double LEASH_MARGIN         = 0.75;
	/**
	 * Blocks around the between point (owner ruling T-423) a hand-off unit's ring spot is taken within: the ring itself
	 * is still sampled around him (Keystone's rings are Player-centred).
	 */
	public static final  double BETWEEN_RADIUS       = 16.0;
	/** Half-angle of a player's view cone, as {@code CopSightlines.inView}. */
	private static final double VIEW_HALF_ANGLE      = Math.PI / 3;

	private final JavaPlugin         plugin;
	private final CopLoader          copLoader;
	private final NpcMarkManager     markManager;
	private final BartizanNpcWeapons bartizanNpcWeapons;
	private final DownedTargetFilter downedTargetFilter;
	private final DetainmentService  detainmentService;
	private final CuffLockRegistry   cuffLockRegistry;
	private volatile Predicate<UUID> searchGuard = id -> false;
	/** Where {@link #insideLeash} measures from for a suspect: him, unless {@link #setLeashAnchor} says otherwise. */
	private volatile Function<Player, Location> leashAnchor = Player::getLocation;
	private volatile CopPerception   perception  = new CopPerception();
	private volatile PatrolServices  patrolServices;

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
	 * {@code allowed} accepts, else somewhere on the ring around him. Either spot must {@link #fits fit}: inside the
	 * leash margin and out of his view (0.21.1, owner ruling R6); a ring spot in view is refused, so {@code null}.
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
		Predicate<Location> fits     = fits(target);
		Location            spawnLoc = findClosestSpawnerLocation(target, allowed.and(fits));

		if (spawnLoc != null) {
			return copNpcFactory.createCop(spawnLoc, tier, false, role);
		}

		spawnLoc = findRingLocation(target);

		// ponytail: the ring search takes no filter, so a spot it picks in his view is refused and the caller retries on
		// its next run; a Keystone ring predicate (as findHiddenSpawnLocation has) replaces this
		if (spawnLoc == null || !fits.test(spawnLoc)) return null;

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
	 * Spawns one due unit: (1) with a hand-off bias, {@link #hiddenRing} between him and the hand-off lead
	 * ({@link #betweenRing}, owner ruling T-423), else ahead of the heading; (2) its station's spawners within
	 * {@code Spawner_Preference_Radius} of {@code target}, allowed and {@link #isOutOfSight out of sight}, nearest first;
	 * (3) without a bias, {@link #hiddenRing} on the station side (within 60 degrees of the target->station bearing);
	 * (4) {@link #hiddenRing} anywhere; (5) {@link #spawnNearPlayer}. Every spot must {@link #fits fit} (0.21.1: inside
	 * the leash margin, out of his view). {@code null} when all fail (the caller requeues the unit).
	 */
	@Nullable
	public CopNpc spawnUnit(Player target, PendingUnit unit, Predicate<Location> allowed) {
		Location            at      = target.getLocation();
		Station             station = unit.station();
		SpawnBias           bias    = unit.bias();
		Predicate<Location> ok      = allowed.and(fits(target));
		boolean             hidden  = true;
		boolean             ring    = true;
		// cause 2: a biased unit is placed before any station spawner, which may stand behind the heading: between him
		// and the lead it heads for (T-423), else ahead of him
		Location spot = bias == null ? null : betweenRing(target, bias, ok);
		if (spot == null && bias != null) spot = hiddenRing(target, ok.and(loc -> bias.ahead(at, loc)));
		if (spot == null && station != null) {
			spot = hiddenStationSpawner(target, station.getId(), ok);
			if (spot != null) ring = false;
		}
		if (spot == null) {
			SpawnBias side = bias == null ? stationSide(at, station) : null;
			if (side != null) spot = hiddenRing(target, ok.and(loc -> side.ahead(at, loc)));
			if (spot == null) spot = hiddenRing(target, ok);
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
	 * Owner ruling T-423: a hand-off unit's hidden ring spot within {@link #BETWEEN_RADIUS} of the point
	 * {@code Cops.Handoff.Spawn_Between} of the way from {@code target}'s live spot to the lead it is sent to
	 * ({@link SpawnBias#lead} of {@link #handoffLead}), that point held within {@code Spawn.Max_Distance} of him and the
	 * spawn leash margin; {@code ok} (leash, R6 view cone, the avoided spawners) still applies. {@code null} with
	 * {@code Spawn_Between} 0, a lead in another world, or no such spot (the caller falls back to the ring ahead). Unless
	 * {@code Spawn_Between} is 0 it logs (debug) {@code HANDOFF_RING <player> center=x,y,z fraction=<f> clamped=<b>
	 * fallback=<b>}, {@code fallback=true} when no spot was found, his own spot the centre for a lead in another world
	 * (T-509).
	 */
	// ponytail: Keystone's rings are Player-centred, so the ring is sampled around him and a spot is only accepted near
	// the between point; a Location-centred findHiddenSpawnLocation upstream would centre it there
	private @Nullable Location betweenRing(Player target, SpawnBias bias, Predicate<Location> ok) {
		HandoffSettings handoff = Objects.requireNonNullElse(configProvider.getHandoffSettings(), HandoffSettings.DEFAULT);
		double          share   = Math.min(1.0, handoff.spawnBetween());
		Location        at      = target.getLocation();
		if (share <= 0) return null;
		Location lead = bias.lead(handoffLead(configProvider));
		if (lead == null || !Objects.equals(lead.getWorld(), at.getWorld())) {
			logRing(target, at, share, false, true); // r7 review: a lead in another world falls back too
			return null;
		}

		Vector toward = new Vector(lead.getX() - at.getX(), 0, lead.getZ() - at.getZ()).multiply(share);
		double leash  = configProvider.getPursuitMaxDistance() * LEASH_MARGIN;
		double reach  = leash > 0 ? Math.min(configProvider.getMaxSpawnDistance(), leash)
		                          : configProvider.getMaxSpawnDistance();
		boolean clamped = toward.length() > reach;
		if (clamped) toward.normalize().multiply(reach);
		Location centre = at.clone().add(toward);
		Location spot   = hiddenRing(target, ok.and(loc -> horizontalSq(loc, centre) <= BETWEEN_RADIUS * BETWEEN_RADIUS));
		logRing(target, centre, share, clamped, spot == null);
		return spot;
	}

	/** T-509: ACC8's R5-spawn-between judge exempts a unit whose fallback is logged beside its UNIT line. */
	private static void logRing(Player target, Location centre, double share, boolean clamped, boolean fallback) {
		if (log.isDebugEnabled())
			log.debug("HANDOFF_RING {} center={},{},{} fraction={} clamped={} fallback={}", target.getName(),
			          centre.getBlockX(), centre.getBlockY(), centre.getBlockZ(), share, clamped, fallback);
	}

	/**
	 * {@code Cops.Handoff.Lead_Blocks}, the lead's length capped at {@link #LEASH_MARGIN} x {@code Pursuit.Max_Distance}
	 * (no leash configured keeps it as set). The cap is on the distance from the squad's last-known spot, never from
	 * the player: a cap against his live position would leak it (R4).
	 */
	public static double handoffLead(CopConfigProvider provider) {
		double lead  = Objects.requireNonNullElse(provider.getHandoffSettings(), HandoffSettings.DEFAULT).leadBlocks();
		double leash = provider.getPursuitMaxDistance() * LEASH_MARGIN;
		return leash > 0 ? Math.min(lead, leash) : lead;
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

	/**
	 * Where a cop may appear for {@code target} (0.21.1): within {@link #LEASH_MARGIN} x {@code Pursuit.Max_Distance}
	 * of his {@link #setLeashAnchor leash anchor} (3D; no leash configured keeps every distance) and not
	 * {@link #inViewOf in his view}.
	 */
	private Predicate<Location> fits(Player target) {
		return spot -> insideLeash(spot, target) && !inViewOf(target, spot);
	}

	private boolean insideLeash(Location spot, Player target) {
		double   leash = configProvider.getPursuitMaxDistance() * LEASH_MARGIN;
		Location at    = leashAnchor.apply(target);
		if (!(leash > 0)) return true;
		return Objects.equals(spot.getWorld(), at.getWorld()) && spot.distanceSquared(at) <= leash * leash;
	}

	/**
	 * Owner ruling R6: {@code spot} is in front of {@code target}'s eyes (within 60 degrees of where he looks) and a
	 * player has a clear line to it ({@link #isOutOfSight} false). A spot he is not looking at, or one a wall hides, is
	 * fine, so "ahead along his heading" only spawns there while he looks elsewhere.
	 */
	private boolean inViewOf(Player target, Location spot) {
		Location eye = target.getEyeLocation();
		if (eye == null || spot.getWorld() == null || !spot.getWorld().equals(eye.getWorld())) return false;
		Vector toward = spot.toVector().add(new Vector(0, 1.6, 0)).subtract(eye.toVector());
		if (toward.lengthSquared() == 0) return true;
		return eye.getDirection().angle(toward) < VIEW_HALF_ANGLE && !isOutOfSight(spot, target);
	}

	/**
	 * Owner ruling R6 for a spot with no suspect (a beat station, 0.21.2 R2, T-587): a real player within
	 * {@code max(24, Visibility_Check_Distance)} has {@code spot} {@link #inViewOf in his view}, so a cop spawned there
	 * would appear before his eyes. The spawn waits for the first run nobody watches.
	 */
	public boolean watched(Location spot) {
		World world = spot.getWorld();
		if (world == null) return false;

		double max = Math.max(CopSightlines.WATCH_MIN_BLOCKS, configProvider.getVisibilityCheckDistance());
		for (Player player : world.getPlayers()) {
			if (player.hasMetadata("NPC") || player.getLocation().distanceSquared(spot) > max * max) continue;
			if (inViewOf(player, spot)) return true;
		}
		return false;
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

	/**
	 * Where the leash margin of a spawn spot is measured from for a suspect: the point his pursuit leash reads from
	 * (CopManager: the squad's last-known spot on the post-escape search, else him). The ring and the spawner search
	 * stay centred on him; only spots also inside that margin are taken.
	 */
	// ponytail: Keystone's EntitySpawner rings are Player-centred, so a search far from him gets no spot; a Location-
	// centred findHiddenSpawnLocation overload upstream would centre search refills on the last-known spot itself
	public void setLeashAnchor(Function<Player, Location> leashAnchor) {
		this.leashAnchor = Objects.requireNonNull(leashAnchor, "leashAnchor");
	}

	/** The sight decisions the cops' behaviours share (0.20.0 seam); read when the factories are rebuilt. */
	public void setPerception(CopPerception perception) {
		this.perception = Objects.requireNonNull(perception, "perception");
		rebuildFactories();
	}

	/** The beat cops' services (0.20.0 lane patrol); read when the factories are rebuilt. */
	public void setPatrolServices(PatrolServices patrolServices) {
		this.patrolServices = Objects.requireNonNull(patrolServices, "patrolServices");
		rebuildFactories();
	}

	private void rebuildFactories() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		this.configProvider = provider;

		CopBehaviorFactory behaviorFactory = new CopBehaviorFactory(provider, () -> this, detainmentService,
		                                                            cuffLockRegistry, id -> searchGuard.test(id), perception,
		                                                            patrolServices);
		this.copNpcFactory = new CopNpcFactory(plugin, provider, behaviorFactory, markManager, bartizanNpcWeapons,
		                                       downedTargetFilter);
	}
}
