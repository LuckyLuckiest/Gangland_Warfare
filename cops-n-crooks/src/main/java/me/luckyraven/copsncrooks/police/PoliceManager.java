package me.luckyraven.copsncrooks.police;

import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.wanted.Wanted;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PoliceManager {

	private final JavaPlugin         plugin;
	private final EntityMarkManager  entityMarkManager;
	private final PoliceSpawner      spawner;
	private final PoliceAIController aiController;

	// Player UUID -> List of police units tracking them
	private final Map<UUID, List<PoliceUnit>> activePolice;
	private final Map<UUID, BukkitTask>       playerAITasks;
	private final Map<UUID, BukkitTask>       playerSpawnTasks;

	private int globalTickCounter;

	public PoliceManager(JavaPlugin plugin, EntityMarkManager entityMarkManager,
						 PathfindingHandler pathfindingHandler) {
		this.plugin            = plugin;
		this.entityMarkManager = entityMarkManager;
		this.spawner           = new PoliceSpawner(plugin, entityMarkManager, pathfindingHandler);
		this.aiController      = new PoliceAIController();
		this.activePolice      = new ConcurrentHashMap<>();
		this.playerAITasks     = new ConcurrentHashMap<>();
		this.playerSpawnTasks  = new ConcurrentHashMap<>();
		this.globalTickCounter = 0;
	}

	public void onWantedLevelStart(Player player, Wanted wanted) {
		UUID playerId = player.getUniqueId();

		if (!wanted.isWanted()) return;

		// Initialize police list for player
		activePolice.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));

		// Start spawn task
		startSpawnTask(playerId, wanted);

		// Start AI task
		startAITask(playerId);
	}

	public void onWantedLevelEnd(Player player) {
		UUID playerId = player.getUniqueId();

		// Stop tasks
		stopSpawnTask(playerId);
		stopAITask(playerId);

		// Despawn all police
		despawnAllForPlayer(playerId);
	}

	public void onWantedLevelChange(Player player, Wanted wanted, int oldLevel, int newLevel) {
		if (oldLevel == 0 && newLevel > 0) {
			onWantedLevelStart(player, wanted);
		} else if (oldLevel > 0 && newLevel == 0) {
			onWantedLevelEnd(player);
		}
		// Level changes while wanted are handled by spawn task
	}

	public void shutdown() {
		// Stop all tasks
		for (UUID playerId : new HashSet<>(playerSpawnTasks.keySet())) {
			stopSpawnTask(playerId);
		}
		for (UUID playerId : new HashSet<>(playerAITasks.keySet())) {
			stopAITask(playerId);
		}

		// Despawn all police
		for (UUID playerId : new HashSet<>(activePolice.keySet())) {
			despawnAllForPlayer(playerId);
		}
	}

	public List<PoliceUnit> getPoliceForPlayer(UUID playerId) {
		List<PoliceUnit> units = activePolice.get(playerId);
		return units != null ? new ArrayList<>(units) : Collections.emptyList();
	}

	public int getActivePoliceCount(UUID playerId) {
		List<PoliceUnit> units = activePolice.get(playerId);
		return units != null ? units.size() : 0;
	}

	public boolean isPoliceUnit(org.bukkit.entity.Entity entity) {
		return PoliceUnit.isPoliceUnit(entity, plugin);
	}

	private void startSpawnTask(UUID playerId, Wanted wanted) {
		if (playerSpawnTasks.containsKey(playerId)) return;

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			Player player = Bukkit.getPlayer(playerId);
			if (player == null || !player.isOnline()) {
				stopSpawnTask(playerId);
				despawnAllForPlayer(playerId);
				return;
			}

			int wantedLevel = wanted.getLevel();
			if (wantedLevel <= 0) {
				stopSpawnTask(playerId);
				return;
			}

			List<PoliceUnit> units = activePolice.get(playerId);
			if (units == null) return;

			// Clean dead units
			units.removeIf(unit -> !unit.isValid());

			// Spawn more if needed
			int targetCops  = PoliceConfig.getCopsForLevel(wantedLevel);
			int currentCops = units.size();

			if (currentCops < targetCops && currentCops < PoliceConfig.MAX_COPS_PER_PLAYER) {
				PoliceUnit newUnit = spawner.spawnPoliceUnit(player, wantedLevel);
				if (newUnit != null) {
					units.add(newUnit);
				}
			}

		}, 20L, PoliceConfig.SPAWN_CHECK_RATE);

		playerSpawnTasks.put(playerId, task);
	}

	private void stopSpawnTask(UUID playerId) {
		BukkitTask task = playerSpawnTasks.remove(playerId);
		if (task != null) {
			task.cancel();
		}
	}

	private void startAITask(UUID playerId) {
		if (playerAITasks.containsKey(playerId)) return;

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			globalTickCounter++;

			Player player = Bukkit.getPlayer(playerId);
			if (player == null || !player.isOnline()) {
				stopAITask(playerId);
				return;
			}

			List<PoliceUnit> units = activePolice.get(playerId);
			if (units == null || units.isEmpty()) return;

			// Process AI for each unit
			Iterator<PoliceUnit> iterator = units.iterator();
			while (iterator.hasNext()) {
				PoliceUnit unit = iterator.next();

				if (!unit.isValid() || unit.getState() == PoliceAIState.DESPAWNING) {
					unit.despawn(entityMarkManager);
					iterator.remove();
					continue;
				}

				aiController.tick(unit, globalTickCounter);

				// Handle despawning state
				if (unit.getState() == PoliceAIState.DESPAWNING) {
					unit.despawn(entityMarkManager);
					iterator.remove();
				}
			}

		}, 5L, PoliceConfig.AI_TICK_RATE);

		playerAITasks.put(playerId, task);
	}

	private void stopAITask(UUID playerId) {
		BukkitTask task = playerAITasks.remove(playerId);
		if (task != null) {
			task.cancel();
		}
	}

	private void despawnAllForPlayer(UUID playerId) {
		List<PoliceUnit> units = activePolice.remove(playerId);
		if (units == null) return;

		for (PoliceUnit unit : units) {
			unit.despawn(entityMarkManager);
		}
	}

}
