package me.luckyraven.copsncrooks.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.state.CopState;
import me.luckyraven.copsncrooks.police.targeting.TargetingManager;
import me.luckyraven.copsncrooks.wanted.Wanted;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all cop NPCs. Handles spawning, AI ticking, and lifecycle management.
 */
public class CopManager {

	private final JavaPlugin        plugin;
	@Getter
	private final CopSpawnManager   spawnManager;
	private final TargetingManager  targetingManager;
	private final CopConfigProvider configProvider;
	private final EntityMarkManager entityMarkManager;

	private final Map<UUID, List<CopNpc>> playerCops;
	private final Map<UUID, BukkitTask>   aiTasks;
	private final Map<UUID, BukkitTask>   spawnTasks;
	private final Set<UUID>               activeCombatAlerts;

	public CopManager(JavaPlugin plugin, CopSpawnManager spawnManager, TargetingManager targetingManager,
					  CopConfigProvider configProvider, EntityMarkManager entityMarkManager) {
		this.plugin             = plugin;
		this.spawnManager       = spawnManager;
		this.targetingManager   = targetingManager;
		this.configProvider     = configProvider;
		this.entityMarkManager  = entityMarkManager;
		this.playerCops         = new ConcurrentHashMap<>();
		this.aiTasks            = new ConcurrentHashMap<>();
		this.spawnTasks         = new ConcurrentHashMap<>();
		this.activeCombatAlerts = ConcurrentHashMap.newKeySet();
	}

	/**
	 * Called when a player becomes wanted. Starts cop spawning and AI for that player.
	 *
	 * @param player the wanted player
	 * @param wanted the wanted data
	 */
	public void onWantedStart(Player player, Wanted wanted) {
		UUID playerId = player.getUniqueId();
		if (!wanted.isWanted()) return;

		targetingManager.registerWanted(player, wanted);
		playerCops.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));

		startSpawnTask(playerId, wanted);
		startAITask(playerId);
	}

	/**
	 * Called when a player is no longer wanted. Despawns all cops and stops tasks.
	 *
	 * @param player the player
	 */
	public void onWantedEnd(Player player) {
		UUID playerId = player.getUniqueId();

		targetingManager.unregisterWanted(playerId);
		stopSpawnTask(playerId);
		stopAITask(playerId);
		clearCombatAlert(playerId);
		despawnAllForPlayer(playerId);
	}

	/**
	 * Called when a player's wanted level changes.
	 *
	 * @param player the player
	 * @param wanted the wanted data
	 * @param oldLevel the previous level
	 * @param newLevel the new level
	 */
	public void onWantedLevelChange(Player player, Wanted wanted, int oldLevel, int newLevel) {
		if (oldLevel == 0 && newLevel > 0) {
			onWantedStart(player, wanted);
		} else if (oldLevel > 0 && newLevel == 0) {
			onWantedEnd(player);
		}
	}

	/**
	 * Called when a cop NPC is attacked by a player. Forces ALL cops assigned to that player into combat mode (alert
	 * system).
	 *
	 * @param copNpc the attacked cop
	 * @param attacker the attacking player
	 */
	public void onCopAttackedAlert(CopNpc copNpc, Player attacker) {
		// Find the target player that this cop is guarding
		UUID targetPlayerId = copNpc.getTargetPlayerId();
		if (targetPlayerId == null) return;

		// Get all cops assigned to this target player
		List<CopNpc> cops = playerCops.get(targetPlayerId);
		if (cops == null || cops.isEmpty()) return;

		// Mark this player's cops as being in active combat alert
		activeCombatAlerts.add(targetPlayerId);

		// Alert ALL cops for this player
		for (CopNpc alertedCop : cops) {
			if (!alertedCop.isValid()) continue;

			onCopAttacked(alertedCop, attacker);
		}
	}

	/**
	 * Called when a cop NPC is attacked by a player. Forces the cop into combat mode.
	 *
	 * @param copNpc the attacked cop
	 * @param attacker the attacking player
	 */
	public void onCopAttacked(CopNpc copNpc, Player attacker) {
		copNpc.setTargetPlayerId(attacker.getUniqueId());
		copNpc.setCombatForced(true);
		copNpc.transitionTo(CopState.COMBAT);
	}

	/**
	 * Checks if there's an active combat alert for a given player.
	 *
	 * @param playerId the player UUID
	 *
	 * @return true if combat alert is active
	 */
	public boolean hasCombatAlert(UUID playerId) {
		return activeCombatAlerts.contains(playerId);
	}

	/**
	 * Returns all cops assigned to a given player.
	 *
	 * @param playerId the player UUID
	 *
	 * @return list of cop NPCs
	 */
	public List<CopNpc> getCopsForPlayer(UUID playerId) {
		List<CopNpc> cops = playerCops.get(playerId);
		return cops != null ? new ArrayList<>(cops) : Collections.emptyList();
	}

	/**
	 * Checks whether the given entity is a cop NPC managed by this system.
	 *
	 * @param entity the entity to check
	 *
	 * @return true if it's a cop
	 */
	public boolean isCopNpc(org.bukkit.entity.Entity entity) {
		for (List<CopNpc> cops : playerCops.values()) {
			for (CopNpc cop : cops) {
				if (!validateEntityCop(entity, cop)) continue;

				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the CopNpc associated with the given entity.
	 *
	 * @param entity the entity
	 *
	 * @return the cop npc, or null
	 */
	public CopNpc findCopByEntity(org.bukkit.entity.Entity entity) {
		for (List<CopNpc> cops : playerCops.values()) {
			for (CopNpc cop : cops) {
				if (!validateEntityCop(entity, cop)) continue;

				return cop;
			}
		}
		return null;
	}

	/**
	 * Shuts down all cops and cancels all tasks. Called on plugin disable.
	 */
	public void shutdown() {
		for (UUID playerId : new HashSet<>(spawnTasks.keySet())) {
			stopSpawnTask(playerId);
		}
		for (UUID playerId : new HashSet<>(aiTasks.keySet())) {
			stopAITask(playerId);
		}
		for (UUID playerId : new HashSet<>(playerCops.keySet())) {
			despawnAllForPlayer(playerId);
		}
	}

	/**
	 * Clears the combat alert for a player when wanted status ends.
	 *
	 * @param playerId the player UUID
	 */
	private void clearCombatAlert(UUID playerId) {
		activeCombatAlerts.remove(playerId);
	}

	private boolean validateEntityCop(Entity entity, CopNpc cop) {
		return cop.isValid() && cop.getNpc().getEntity().getUniqueId().equals(entity.getUniqueId());
	}

	/**
	 * Starts the periodic spawn task for a player.
	 *
	 * @param playerId the player UUID
	 * @param wanted the wanted data
	 */
	private void startSpawnTask(UUID playerId, Wanted wanted) {
		if (spawnTasks.containsKey(playerId)) return;

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

			List<CopNpc> cops = playerCops.get(playerId);
			if (cops == null) return;

			cops.removeIf(cop -> !cop.isValid() || cop.isMarkedForRemoval());

			int targetCount  = spawnManager.getTargetCopCount(wantedLevel);
			int currentCount = cops.size();
			int tier         = spawnManager.getTierForWantedLevel(wantedLevel);

			// Spawn all missing cops in one pass so a full wipe is recovered in a single interval
			while (currentCount < targetCount && currentCount < configProvider.getMaxCopsPerPlayer()) {
				CopNpc newCop = spawnManager.spawnNearPlayer(player, tier);
				if (newCop == null) break; // no valid location found - stop trying this interval

				newCop.setTargetPlayerId(playerId);

				// New spawns pursue immediately; escalate to combat if a combat alert is active
				if (hasCombatAlert(playerId)) {
					newCop.setCombatForced(true);
					newCop.transitionTo(CopState.COMBAT);
				} else {
					newCop.transitionTo(CopState.PURSUING);
				}

				cops.add(newCop);
				currentCount++;
			}
		}, 20L, configProvider.getSpawnCheckRate());

		spawnTasks.put(playerId, task);
	}

	/**
	 * Stops the spawn task for a player.
	 *
	 * @param playerId the player UUID
	 */
	private void stopSpawnTask(UUID playerId) {
		BukkitTask task = spawnTasks.remove(playerId);
		if (task != null) task.cancel();
	}

	/**
	 * Starts the AI tick task for a player's cops.
	 *
	 * @param playerId the player UUID
	 */
	private void startAITask(UUID playerId) {
		if (aiTasks.containsKey(playerId)) return;

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			Player player = Bukkit.getPlayer(playerId);
			if (player == null || !player.isOnline()) {
				stopAITask(playerId);
				return;
			}

			List<CopNpc> cops = playerCops.get(playerId);
			if (cops == null || cops.isEmpty()) return;

			Iterator<CopNpc> iterator = cops.iterator();
			while (iterator.hasNext()) {
				CopNpc cop = iterator.next();

				if (!cop.isValid() || cop.isMarkedForRemoval()) {
					cop.destroy(entityMarkManager);
					iterator.remove();
					continue;
				}

				Player target = resolveTarget(cop, player);
				cop.tick(target);
			}
		}, 0L, configProvider.getAiTickRate());

		aiTasks.put(playerId, task);
	}

	/**
	 * Resolves the current target for a cop. Switches target if current is no longer wanted.
	 *
	 * @param cop the cop NPC
	 * @param defaultTarget the default target player
	 *
	 * @return the resolved target
	 */
	private Player resolveTarget(CopNpc cop, Player defaultTarget) {
		UUID currentTargetId = cop.getTargetPlayerId();

		if (currentTargetId != null && targetingManager.isWanted(currentTargetId)) {
			Player currentTarget = Bukkit.getPlayer(currentTargetId);
			if (currentTarget != null && currentTarget.isOnline()) {
				return currentTarget;
			}
		}

		Player newTarget = targetingManager.findBestTarget(defaultTarget);
		if (newTarget != null) {
			cop.setTargetPlayerId(newTarget.getUniqueId());
			return newTarget;
		}

		cop.setTargetPlayerId(null);
		if (cop.getCurrentState() != CopState.RETURNING && cop.getCurrentState() != CopState.IDLE) {
			cop.transitionTo(CopState.RETURNING);
		}
		return null;
	}

	/**
	 * Stops the AI task for a player.
	 *
	 * @param playerId the player UUID
	 */
	private void stopAITask(UUID playerId) {
		BukkitTask task = aiTasks.remove(playerId);
		if (task != null) task.cancel();
	}

	/**
	 * Despawns and removes all cops assigned to a player.
	 *
	 * @param playerId the player UUID
	 */
	private void despawnAllForPlayer(UUID playerId) {
		List<CopNpc> cops = playerCops.remove(playerId);
		if (cops == null) return;

		for (CopNpc cop : cops) {
			cop.destroy(entityMarkManager);
		}
	}
}