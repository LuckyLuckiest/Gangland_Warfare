package me.luckyraven.copsncrooks.police;

import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class PoliceSpawner {

	private final Logger logger = LogManager.getLogger(PoliceSpawner.class.getSimpleName());

	private final JavaPlugin         plugin;
	private final EntityMarkManager  entityMarkManager;
	private final PathfindingHandler pathfindingHandler;

	public PoliceSpawner(JavaPlugin plugin, EntityMarkManager entityMarkManager,
						 PathfindingHandler pathfindingHandler) {
		this.plugin             = plugin;
		this.entityMarkManager  = entityMarkManager;
		this.pathfindingHandler = pathfindingHandler;
	}

	public PoliceUnit spawnPoliceUnit(Player target, int wantedLevel) {
		Location spawnLoc = findSpawnLocation(target);
		if (spawnLoc == null) {
			logger.warn("Failed to find spawn location for police unit");
			return null;
		}

		World world = target.getWorld();

		Mob policeEntity = (Mob) world.spawnEntity(spawnLoc, PoliceConfig.POLICE_ENTITY_TYPE);

		logger.info("Spawned police at {}, {}, {} (distance: {})", spawnLoc.getBlockX(), spawnLoc.getBlockY(),
					spawnLoc.getBlockZ(), spawnLoc.distance(target.getLocation()));

		return new PoliceUnit(plugin, policeEntity, target, wantedLevel, entityMarkManager, pathfindingHandler);
	}

	private Location findSpawnLocation(Player player) {
		Location          playerLoc = player.getLocation();
		World             world     = player.getWorld();
		ThreadLocalRandom random    = ThreadLocalRandom.current();

		// Try multiple times to find valid spawn
		for (int attempt = 0; attempt < 20; attempt++) {
			double angle    = random.nextDouble(0, 2 * Math.PI);
			double distance = random.nextDouble(PoliceConfig.MIN_SPAWN_DISTANCE, PoliceConfig.MAX_SPAWN_DISTANCE);

			double x = playerLoc.getX() + Math.cos(angle) * distance;
			double z = playerLoc.getZ() + Math.sin(angle) * distance;

			// Check if chunk is loaded
			int chunkX = (int) x >> 4;
			int chunkZ = (int) z >> 4;

			if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

			// Find ground level near player's Y level, not surface
			Location spawnLoc = findGroundNearY(world, x, z, playerLoc.getBlockY());
			if (spawnLoc == null) continue;

			// Validate spawn location
			if (!isValidSpawnLocation(spawnLoc, player)) continue;

			return spawnLoc;
		}

		return null;
	}

	private Location findGroundNearY(World world, double x, double z, int targetY) {
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);

		// Search within a vertical range around player's Y level
		int searchRange = 10;

		// Search downward from player's level first
		for (int y = targetY; y >= targetY - searchRange && y >= world.getMinHeight(); y--) {
			if (!isValidGround(world, blockX, y, blockZ)) continue;

			return new Location(world, x, y + 1, z);
		}

		// Then search upward
		for (int y = targetY + 1; y <= targetY + searchRange && y < world.getMaxHeight() - 2; y++) {
			if (!isValidGround(world, blockX, y, blockZ)) continue;

			return new Location(world, x, y + 1, z);
		}

		return null;
	}

	private boolean isValidGround(World world, int x, int y, int z) {
		Block ground = world.getBlockAt(x, y, z);
		Block feet   = world.getBlockAt(x, y + 1, z);
		Block head   = world.getBlockAt(x, y + 2, z);

		return ground.getType().isSolid() && feet.isPassable() && head.isPassable();
	}

	private boolean isValidSpawnLocation(Location loc, Player player) {
		World world = loc.getWorld();
		if (world == null) return false;

		// Must be outside player's direct view (behind or to the side)
		Location playerLoc = player.getLocation();

		// Check if spawn is roughly behind player
		double playerYaw    = Math.toRadians(playerLoc.getYaw());
		double toSpawnAngle = Math.atan2(loc.getZ() - playerLoc.getZ(), loc.getX() - playerLoc.getX());
		double angleDiff    = Math.abs(normalizeAngle(toSpawnAngle - playerYaw));

		// Spawn should be at least 90 degrees from player facing direction
		return angleDiff > Math.PI / 2;
	}

	private double normalizeAngle(double angle) {
		while (angle > Math.PI) angle -= 2 * Math.PI;
		while (angle < -Math.PI) angle += 2 * Math.PI;
		return angle;
	}

}