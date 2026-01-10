package me.luckyraven.copsncrooks.police;

import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class PoliceSpawner {

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
		if (spawnLoc == null) return null;

		World world = target.getWorld();

		Mob policeEntity = (Mob) world.spawnEntity(spawnLoc, PoliceConfig.POLICE_ENTITY_TYPE);

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

			// Find ground level
			int      groundY  = world.getHighestBlockYAt((int) x, (int) z);
			Location spawnLoc = new Location(world, x, groundY + 1, z);

			// Validate spawn location
			if (isValidSpawnLocation(spawnLoc, player)) {
				return spawnLoc;
			}
		}

		return null;
	}

	private boolean isValidSpawnLocation(Location loc, Player player) {
		World world = loc.getWorld();
		if (world == null) return false;

		Block ground = world.getBlockAt(loc.clone().subtract(0, 1, 0));
		Block feet   = world.getBlockAt(loc);
		Block head   = world.getBlockAt(loc.clone().add(0, 1, 0));

		// Ground must be solid, space above must be passable
		if (!ground.getType().isSolid()) return false;
		if (!feet.isPassable() || !head.isPassable()) return false;

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