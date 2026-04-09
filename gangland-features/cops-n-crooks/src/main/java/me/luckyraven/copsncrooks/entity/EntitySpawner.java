package me.luckyraven.copsncrooks.entity;

import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abstract base for NPC spawner managers.
 * <p>
 * Provides a persistent registry of {@link EntitySpawnerPoint} locations and all shared spawn-location-finding logic
 * (ground search, indoor/outdoor matching, behind-player check, etc.). Subclasses provide the entity-specific spawning
 * action and the factory method for creating concrete {@link EntitySpawnerPoint} instances.
 *
 * @param <S> the concrete spawner-point type
 */
public abstract class EntitySpawner<S extends EntitySpawnerPoint> implements BeanLifecycle {

	public int ID = 0;

	protected final SpawnConfigProvider config;
	protected final IRepository<S>      repository;
	protected final Map<Integer, S>     spawners;

	protected EntitySpawner(SpawnConfigProvider config, IRepository<S> repository) {
		this.config     = config;
		this.repository = repository;
		this.spawners   = new ConcurrentHashMap<>();

		repository.setDataSupplier(spawners::values);
	}

	// ── Abstract ─────────────────────────────────────────────────────────────

	/**
	 * Creates a concrete spawner-point instance for the given id and location.
	 */
	protected abstract S createSpawnerPoint(int id, Location location);

	// ── Spawner registry ──────────────────────────────────────────────────────

	public void reloadSpawners() {
		loadStoredSpawners();
	}

	@Override
	public void onClear() {
		spawners.clear();
		ID = 0;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		loadStoredSpawners();
	}

	public void setSpawnerLocation(Location location) {
		ID++;
		S spawner = spawners.computeIfAbsent(ID, ignored -> createSpawnerPoint(ID, location));
		spawner.setLocation(location);
		repository.save(spawner);
	}

	public void removeSpawner(int id) {
		S spawner = spawners.remove(id);
		if (spawner != null) {
			repository.delete(spawner);
		}
	}

	public List<Integer> getSpawnerIds() {
		return new ArrayList<>(spawners.keySet());
	}

	public List<Location> getSpawnerLocations() {
		List<Location> locations = new ArrayList<>();
		for (S spawner : spawners.values()) {
			locations.add(spawner.getLocation());
		}
		return locations;
	}

	public List<S> getSpawners() {
		return new ArrayList<>(spawners.values());
	}

	@Nullable
	public Location getSpawnerLocation(int id) {
		S spawner = spawners.get(id);
		return spawner == null ? null : spawner.getLocation();
	}

	// ── Location-finding (shared by all subclasses) ───────────────────────────

	/**
	 * Checks whether any online player (other than the target) can see the given location.
	 */
	public boolean isVisibleToOtherPlayers(Location location, Player excludePlayer) {
		if (location == null || location.getWorld() == null) return false;

		for (Player player : location.getWorld().getPlayers()) {
			if (player.equals(excludePlayer)) continue;

			double distance = player.getLocation().distance(location);
			if (distance > config.getVisibilityCheckDistance()) continue;

			Location playerLoc = player.getLocation();
			double   playerYaw = Math.toRadians(playerLoc.getYaw());
			double toLocAngle = Math.atan2(location.getZ() - playerLoc.getZ(),
			                               location.getX() - playerLoc.getX());
			double angleDiff = Math.abs(normalizeAngle(toLocAngle - playerYaw));

			if (angleDiff < Math.PI / 3) return true;
		}

		return false;
	}

	/**
	 * Finds the closest registered spawner to the player within the configured preference radius.
	 *
	 * @param player the target player
	 *
	 * @return the closest spawner location within range, or null
	 */
	@Nullable
	protected Location findClosestSpawnerLocation(Player player) {
		Location playerLoc     = player.getLocation();
		Location closest       = null;
		double   prefRadius    = config.getSpawnerPreferenceRadius();
		double   closestDistSq = prefRadius * prefRadius;

		for (S spawner : spawners.values()) {
			Location loc   = spawner.getLocation();
			World    world = loc.getWorld();
			if (world == null || !world.equals(playerLoc.getWorld())) continue;

			double distSq = loc.distanceSquared(playerLoc);
			if (distSq >= closestDistSq) continue;

			closestDistSq = distSq;
			closest       = loc;
		}

		return closest;
	}

	/**
	 * Finds a valid spawn location around the player using a two-phase approach: phase 1 — preferred ring, behind the
	 * player; phase 2 — shrinking ring, unconstrained.
	 *
	 * @param player the target player
	 *
	 * @return a valid spawn location, or null if none found
	 */
	@Nullable
	protected Location findSpawnLocation(Player player) {
		double minDist    = config.getMinSpawnDistance();
		double maxDist    = config.getMaxSpawnDistance();
		double p1Min      = config.getPhase1MinDistance();
		double shrinkStep = config.getSpawnRadiusShrinkStep();

		boolean playerOutdoor = isOutdoor(player.getLocation());

		for (int attempt = 0; attempt < config.getSpawnPhase1Attempts(); attempt++) {
			Location loc = trySingleSpawnAttempt(player, p1Min, maxDist, true, playerOutdoor);
			if (loc != null) return loc;
		}

		for (double max = maxDist; max >= minDist; max -= shrinkStep) {
			double min = Math.max(minDist, max - shrinkStep);
			for (int attempt = 0; attempt < config.getSpawnPhase2Attempts(); attempt++) {
				Location loc = trySingleSpawnAttempt(player, min, max, false, playerOutdoor);
				if (loc != null) return loc;
			}
		}

		return null;
	}

	// ── Private location helpers ───────────────────────────────────────────────

	protected boolean isOutdoor(Location location) {
		World world = location.getWorld();
		if (world == null) return true;
		return world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) < location.getBlockY();
	}

	protected double normalizeAngle(double angle) {
		while (angle > Math.PI) angle -= 2 * Math.PI;
		while (angle < -Math.PI) angle += 2 * Math.PI;
		return angle;
	}

	private void loadStoredSpawners() {
		spawners.clear();
		ID = 0;
		Collection<S> loadedSpawners = repository.loadAll();
		for (S spawner : loadedSpawners) {
			spawners.put(spawner.getId(), spawner);
			if (spawner.getId() > ID) {
				ID = spawner.getId();
			}
		}
	}

	@Nullable
	private Location trySingleSpawnAttempt(Player player, double minDist, double maxDist,
	                                       boolean requireBehind, boolean playerOutdoor) {
		Location          playerLoc = player.getLocation();
		World             world     = player.getWorld();
		ThreadLocalRandom random    = ThreadLocalRandom.current();

		double angle    = random.nextDouble(0, 2 * Math.PI);
		double distance = minDist >= maxDist ? minDist : random.nextDouble(minDist, maxDist);

		double x = playerLoc.getX() + Math.cos(angle) * distance;
		double z = playerLoc.getZ() + Math.sin(angle) * distance;

		int chunkX = ((int) Math.floor(x)) >> 4;
		int chunkZ = ((int) Math.floor(z)) >> 4;

		if (!world.isChunkLoaded(chunkX, chunkZ)) return null;

		Location spawnLoc = findGroundNearY(world, x, z,
		                                    playerLoc.getBlockY() + config.getSpawnYOffset());
		if (spawnLoc == null) return null;
		if (requireBehind && !isSpawnBehindPlayer(spawnLoc, player)) return null;
		if (isOutdoor(spawnLoc) != playerOutdoor) return null;

		return spawnLoc;
	}

	@Nullable
	private Location findGroundNearY(World world, double x, double z, int targetY) {
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);

		int searchRange = config.getVerticalSearchRange();
		int minY        = Math.max(world.getMinHeight(), targetY - searchRange);
		int maxY        = Math.min(world.getMaxHeight() - 3, targetY + searchRange);

		for (int offset = 0; offset <= searchRange; offset++) {
			int downY = targetY - offset;
			if (downY >= minY && isValidGround(world, blockX, downY, blockZ)) {
				return createCenteredSpawnLocation(world, blockX, downY, blockZ);
			}

			if (offset == 0) continue;

			int upY = targetY + offset;
			if (upY <= maxY && isValidGround(world, blockX, upY, blockZ)) {
				return createCenteredSpawnLocation(world, blockX, upY, blockZ);
			}
		}

		return null;
	}

	private boolean isValidGround(World world, int x, int y, int z) {
		Block ground    = world.getBlockAt(x, y, z);
		Block feet      = world.getBlockAt(x, y + 1, z);
		Block head      = world.getBlockAt(x, y + 2, z);
		Block aboveHead = world.getBlockAt(x, y + 3, z);

		if (!ground.getType().isSolid()) return false;
		if (!feet.isEmpty() || !head.isEmpty() || !aboveHead.isEmpty()) return false;

		return hasEnoughHorizontalClearance(world, x, y + 1, z) &&
		       hasEnoughHorizontalClearance(world, x, y + 2, z);
	}

	private boolean hasEnoughHorizontalClearance(World world, int x, int y, int z) {
		int openSides = 0;
		if (world.getBlockAt(x + 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x - 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z + 1).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z - 1).isEmpty()) openSides++;
		return openSides >= config.getMinOpenHorizontalSides();
	}

	private Location createCenteredSpawnLocation(World world, int blockX, int groundY, int blockZ) {
		return new Location(world, blockX + 0.5, groundY + 1, blockZ + 0.5);
	}

	private boolean isSpawnBehindPlayer(Location spawnLoc, Player player) {
		Location playerLoc = player.getLocation();
		double   playerYaw = Math.toRadians(playerLoc.getYaw());
		double toSpawnAngle = Math.atan2(spawnLoc.getZ() - playerLoc.getZ(),
		                                 spawnLoc.getX() - playerLoc.getX());
		double angleDiff = Math.abs(normalizeAngle(toSpawnAngle - playerYaw));
		return angleDiff > Math.PI / 2;
	}
}
