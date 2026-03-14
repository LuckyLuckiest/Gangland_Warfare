package me.luckyraven.copsncrooks.police.spawn;

import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.npc.CopNpcFactory;
import me.luckyraven.persistence.repository.IRepository;
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

public class CopSpawnManager {

	private static final double MIN_SPAWN_DISTANCE        = 10.0;
	private static final double MAX_SPAWN_DISTANCE        = 50.0;
	private static final double SPAWN_RADIUS_SHRINK_STEP  = 5.0;
	private static final int    VERTICAL_SEARCH_RANGE     = 10;
	private static final int    SPAWN_Y_OFFSET            = 0;
	private static final int    MIN_OPEN_HORIZONTAL_SIDES = 2;

	public static int ID = 0;

	private final CopNpcFactory            copNpcFactory;
	private final CopConfigProvider        configProvider;
	private final IRepository<CopSpawner>  repository;
	private final Map<Integer, CopSpawner> spawners;

	public CopSpawnManager(CopNpcFactory copNpcFactory, CopConfigProvider configProvider,
						   IRepository<CopSpawner> repository) {
		this.copNpcFactory  = copNpcFactory;
		this.configProvider = configProvider;
		this.repository     = repository;
		this.spawners       = new ConcurrentHashMap<>();

		loadStoredSpawners();

		repository.setDataSupplier(spawners::values);
	}

	public void reloadSpawners() {
		loadStoredSpawners();
	}

	public void setSpawnerLocation(Location location) {
		ID++;

		CopSpawner spawner = spawners.computeIfAbsent(ID, ignored -> new CopSpawner(ID, location));

		spawner.setLocation(location);
	}

	public void removeSpawner(int id) {
		CopSpawner spawner = spawners.remove(id);
		if (spawner != null) {
			repository.delete(spawner);
		}
	}

	public List<Integer> getSpawnerIds() {
		return new ArrayList<>(spawners.keySet());
	}

	public List<Location> getSpawnerLocations() {
		List<Location> locations = new ArrayList<>();

		for (CopSpawner spawner : spawners.values()) {
			locations.add(spawner.getLocation());
		}

		return locations;
	}

	@Nullable
	public Location getSpawnerLocation(int id) {
		CopSpawner spawner = spawners.get(id);
		return spawner == null ? null : spawner.getLocation();
	}

	/**
	 * Spawns a cop NPC near the given player with the specified tier.
	 *
	 * @param target the player to spawn near
	 * @param tier the cop tier
	 *
	 * @return the spawned CopNpc, or null if no valid location was found
	 */
	public CopNpc spawnNearPlayer(Player target, int tier) {
		Location spawnLoc = findSpawnLocation(target);

		if (spawnLoc == null) {
			spawnLoc = findStoredSpawnerLocation(target.getWorld());
		}

		if (spawnLoc == null) return null;

		return copNpcFactory.createCop(spawnLoc, tier);
	}

	/**
	 * Spawns a cop NPC at a specific configured spawn location.
	 *
	 * @param location the spawn location
	 * @param tier the cop tier
	 *
	 * @return the spawned CopNpc, or null on failure
	 */
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

	/**
	 * Checks whether any online player (other than the target) can directly see the given location. Used to prevent
	 * despawning in front of bystanders.
	 *
	 * @param location the location to check
	 * @param excludePlayer the player to exclude from the check (the cop's target)
	 *
	 * @return true if visible to another player
	 */
	public boolean isVisibleToOtherPlayers(Location location, Player excludePlayer) {
		if (location == null || location.getWorld() == null) return false;

		for (Player player : location.getWorld().getPlayers()) {
			if (player.equals(excludePlayer)) continue;

			double distance = player.getLocation().distance(location);
			if (distance > 48) continue;

			// Check if the cop is in the player's forward FOV
			Location playerLoc  = player.getLocation();
			double   playerYaw  = Math.toRadians(playerLoc.getYaw());
			double   toLocAngle = Math.atan2(location.getZ() - playerLoc.getZ(), location.getX() - playerLoc.getX());
			double   angleDiff  = Math.abs(normalizeAngle(toLocAngle - playerYaw));

			// Within ~110 degree forward cone
			if (angleDiff < Math.PI / 3) {
				return true;
			}
		}

		return false;
	}

	private void loadStoredSpawners() {
		spawners.clear();

		Collection<CopSpawner> loadedSpawners = repository.loadAll();
		for (CopSpawner spawner : loadedSpawners) {
			spawners.put(spawner.getId(), spawner);
		}
	}

	/**
	 * Finds a valid spawn location around the target player. Prefers locations close to the player's current Y level,
	 * including inside buildings, and avoids placing the NPC inside walls by spawning at the center of the block
	 * column.
	 *
	 * @param world the world
	 *
	 * @return a spawn location, or null
	 */
	@Nullable
	private Location findStoredSpawnerLocation(World world) {
		List<Location> candidates = new ArrayList<>();

		for (CopSpawner spawner : spawners.values()) {
			Location location = spawner.getLocation();
			if (location.getWorld() == null) continue;
			if (!location.getWorld().getUID().equals(world.getUID())) continue;

			candidates.add(location);
		}

		if (candidates.isEmpty()) {
			return null;
		}

		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	/**
	 * Finds a valid spawn location around the player using two phases:
	 * <ol>
	 *   <li>Phase 1 — 20 attempts in the preferred 30–50 block ring, restricted to positions behind the player so
	 *       the NPC does not pop into view.</li>
	 *   <li>Phase 2 — if phase 1 fails entirely (player is stationary, against a wall, etc.), the ring radius
	 *       shrinks by {@value SPAWN_RADIUS_SHRINK_STEP} blocks per step down to {@value MIN_SPAWN_DISTANCE} and
	 *       the behind-player constraint is dropped, giving every loaded angle a fair chance.</li>
	 * </ol>
	 */
	private Location findSpawnLocation(Player player) {
		// Phase 1: preferred ring, behind-player only
		for (int attempt = 0; attempt < 20; attempt++) {
			Location loc = trySingleSpawnAttempt(player, 30.0, MAX_SPAWN_DISTANCE, true);
			if (loc != null) return loc;
		}

		// Phase 2: shrink the ring progressively, drop the direction constraint
		for (double maxDist = MAX_SPAWN_DISTANCE; maxDist >= MIN_SPAWN_DISTANCE; maxDist -= SPAWN_RADIUS_SHRINK_STEP) {
			double minDist = Math.max(MIN_SPAWN_DISTANCE, maxDist - SPAWN_RADIUS_SHRINK_STEP);
			for (int attempt = 0; attempt < 15; attempt++) {
				Location loc = trySingleSpawnAttempt(player, minDist, maxDist, false);
				if (loc != null) return loc;
			}
		}

		return null;
	}

	/**
	 * Picks one random point in the given distance ring and checks whether it is a valid spawn site.
	 *
	 * @param player the target player
	 * @param minDist minimum radial distance
	 * @param maxDist maximum radial distance
	 * @param requireBehind whether the candidate must be outside the player's forward FOV
	 *
	 * @return a valid spawn location, or {@code null} if this attempt failed
	 */
	@Nullable
	private Location trySingleSpawnAttempt(Player player, double minDist, double maxDist, boolean requireBehind) {
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

		Location spawnLoc = findGroundNearY(world, x, z, playerLoc.getBlockY() + SPAWN_Y_OFFSET);
		if (spawnLoc == null) return null;

		if (requireBehind && !isSpawnBehindPlayer(spawnLoc, player)) return null;

		return spawnLoc;
	}

	/**
	 * Finds ground level near the target Y coordinate. Prefers the closest valid Y to the player, checking the same
	 * level first, then moving downward before upward. This allows indoor spawns and avoids preferring rooftops above
	 * buildings.
	 *
	 * @param world the world
	 * @param x the x coordinate
	 * @param z the z coordinate
	 * @param targetY the target y level
	 *
	 * @return the ground location, or null
	 */
	private Location findGroundNearY(World world, double x, double z, int targetY) {
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);

		int minY = Math.max(world.getMinHeight(), targetY - VERTICAL_SEARCH_RANGE);
		int maxY = Math.min(world.getMaxHeight() - 3, targetY + VERTICAL_SEARCH_RANGE);

		for (int offset = 0; offset <= VERTICAL_SEARCH_RANGE; offset++) {
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

	/**
	 * Checks if the block at the given coordinates forms valid ground for spawning. Requires solid ground, open feet
	 * and head space, one extra air block above the head, and enough horizontal clearance so the NPC is not boxed in.
	 *
	 * @param world the world
	 * @param x x coordinate
	 * @param y y coordinate
	 * @param z z coordinate
	 *
	 * @return true if valid
	 */
	private boolean isValidGround(World world, int x, int y, int z) {
		Block ground    = world.getBlockAt(x, y, z);
		Block feet      = world.getBlockAt(x, y + 1, z);
		Block head      = world.getBlockAt(x, y + 2, z);
		Block aboveHead = world.getBlockAt(x, y + 3, z);

		if (!ground.getType().isSolid()) return false;
		if (!feet.isEmpty() || !head.isEmpty() || !aboveHead.isEmpty()) return false;

		return hasEnoughHorizontalClearance(world, x, y + 1, z) && hasEnoughHorizontalClearance(world, x, y + 2, z);
	}

	/**
	 * Requires at least a minimum amount of horizontal open space around the spawn column. This keeps indoor spawning
	 * possible while rejecting cramped wall pockets and tight cages.
	 *
	 * @param world the world
	 * @param x center x
	 * @param y y level to validate
	 * @param z center z
	 *
	 * @return true if enough sides are open
	 */
	private boolean hasEnoughHorizontalClearance(World world, int x, int y, int z) {
		int openSides = 0;

		if (world.getBlockAt(x + 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x - 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z + 1).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z - 1).isEmpty()) openSides++;

		return openSides >= MIN_OPEN_HORIZONTAL_SIDES;
	}

	/**
	 * Creates a spawn location centered within the target block column to reduce the chance of spawning clipped into a
	 * neighboring wall or corner.
	 *
	 * @param world the world
	 * @param blockX x coordinate of the ground block
	 * @param groundY y coordinate of the ground block
	 * @param blockZ z coordinate of the ground block
	 *
	 * @return centered spawn location
	 */
	private Location createCenteredSpawnLocation(World world, int blockX, int groundY, int blockZ) {
		return new Location(world, blockX + 0.5, groundY + 1, blockZ + 0.5);
	}

	/**
	 * Verifies the spawn location is outside the player's direct forward vision.
	 *
	 * @param spawnLoc the candidate spawn location
	 * @param player the player
	 *
	 * @return true if the spawn is behind the player
	 */
	private boolean isSpawnBehindPlayer(Location spawnLoc, Player player) {
		Location playerLoc    = player.getLocation();
		double   playerYaw    = Math.toRadians(playerLoc.getYaw());
		double   toSpawnAngle = Math.atan2(spawnLoc.getZ() - playerLoc.getZ(), spawnLoc.getX() - playerLoc.getX());
		double   angleDiff    = Math.abs(normalizeAngle(toSpawnAngle - playerYaw));

		return angleDiff > Math.PI / 2;
	}

	/**
	 * Normalizes an angle to the range [-PI, PI].
	 *
	 * @param angle the angle in radians
	 *
	 * @return the normalized angle
	 */
	private double normalizeAngle(double angle) {
		while (angle > Math.PI) angle -= 2 * Math.PI;
		while (angle < -Math.PI) angle += 2 * Math.PI;
		return angle;
	}
}