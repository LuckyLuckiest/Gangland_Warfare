package me.luckyraven.copsncrooks.npc.police;

import lombok.Getter;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpcRegistry;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.state.CopState;
import me.luckyraven.copsncrooks.npc.police.targeting.TargetingManager;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all cop NPCs. Handles spawning, AI ticking, and lifecycle management.
 */
public class CopManager implements BeanLifecycle {

	private final JavaPlugin            plugin;
	@Getter
	private final CopSpawnManager       spawnManager;
	private final TargetingManager      targetingManager;
	private final CopLoader             copLoader;
	private final EntityMarkManager     entityMarkManager;
	private final DetainmentService     detainmentService;
	private final Map<UUID, CopGroup>   groups;
	private final Map<UUID, BukkitTask> aiTasks;
	private final Map<UUID, BukkitTask> spawnTasks;
	private final Set<UUID>             activeCombatAlerts;
	private final Set<UUID>             copAttackers;
	private final CivilianNpcRegistry   civilianNpcRegistry;
	private       CopConfigProvider     configProvider;

	public CopManager(JavaPlugin plugin, CopSpawnManager spawnManager, TargetingManager targetingManager,
	                  CopLoader copLoader, EntityMarkManager entityMarkManager,
	                  DetainmentService detainmentService, CivilianNpcRegistry civilianNpcRegistry) {
		this.plugin            = plugin;
		this.spawnManager      = spawnManager;
		this.targetingManager  = targetingManager;
		this.copLoader         = copLoader;
		this.configProvider    = copLoader.getLoadedProvider();
		this.entityMarkManager = entityMarkManager;
		this.detainmentService = detainmentService;

		this.civilianNpcRegistry = civilianNpcRegistry;
		this.groups              = new ConcurrentHashMap<>();
		this.aiTasks             = new ConcurrentHashMap<>();
		this.spawnTasks          = new ConcurrentHashMap<>();
		this.activeCombatAlerts  = ConcurrentHashMap.newKeySet();
		this.copAttackers        = ConcurrentHashMap.newKeySet();
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
		groups.computeIfAbsent(playerId, CopGroup::new);

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
		clearCombatAlert(playerId);

		CopGroup group = groups.get(playerId);
		if (group == null || group.isEmpty()) {
			stopAITask(playerId);
			groups.remove(playerId);
			return;
		}

		// Clear targeting only for cops still pointing at the now-unwanted player.
		// Cops that already retargeted to an attacker keep their state so resolveTarget
		// can evaluate them correctly on the next AI tick.
		for (CopNpc cop : group.getCops()) {
			if (!playerId.equals(cop.getTargetPlayerId())) continue;

			cop.setTargetPlayerId(null);
			cop.setCombatForced(false);
		}

		// Do not stop the AI task or despawn — let cops organically find new targets.
		// The AI task will self-terminate once all cops have returned and despawned.
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
		// Always force the directly attacked cop into combat with the attacker,
		// regardless of whether it currently has a target or belongs to a group.
		onCopAttacked(copNpc, attacker);

		// Alert all other cops in the same group (if any).
		// The cop may not have a targetPlayerId yet (idle/returning), so we cannot
		// rely on groups.get(targetPlayerId). Instead, find the group containing this cop.
		CopGroup group = findGroupContaining(copNpc);
		if (group == null || group.isEmpty()) return;

		activeCombatAlerts.add(group.getTargetPlayerId());

		for (CopNpc alertedCop : group.getCops()) {
			if (!alertedCop.isValid()) continue;
			if (alertedCop == copNpc) continue;

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
		copAttackers.add(attacker.getUniqueId());
		copNpc.setTargetPlayerId(attacker.getUniqueId());
		copNpc.setCombatForced(true);
		copNpc.transitionTo(CopState.COMBAT);
	}

	/**
	 * Removes a player from the cop-attacker registry. Should be called when the player dies or leaves, so that cops no
	 * longer treat them as a combat target after respawn.
	 * <p>
	 * Also eagerly clears this player from any cop's target so cops don't continue navigating to the death location.
	 * The next AI tick's {@code resolveTarget} will transition idle cops to RETURNING if no other target is found.
	 *
	 * @param playerId the player UUID
	 */
	public void removeCopAttacker(UUID playerId) {
		copAttackers.remove(playerId);

		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
				if (!playerId.equals(cop.getTargetPlayerId())) continue;

				cop.setTargetPlayerId(null);
				cop.setCombatForced(false);
			}
		}
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
		CopGroup group = groups.get(playerId);
		return group != null ? new ArrayList<>(group.getCops()) : Collections.emptyList();
	}

	/**
	 * Checks whether the given entity is a cop NPC managed by this system.
	 *
	 * @param entity the entity to check
	 *
	 * @return true if it's a cop
	 */
	public boolean isCopNpc(org.bukkit.entity.Entity entity) {
		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
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
		for (CopGroup group : groups.values()) {
			for (CopNpc cop : group.getCops()) {
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
		for (UUID playerId : new HashSet<>(groups.keySet())) {
			despawnAllForPlayer(playerId);
		}
		copAttackers.clear();
	}

	@Override
	public void onPreClear() {
		shutdown();
	}

	@Override
	public void onClear() {
		groups.clear();
		aiTasks.clear();
		spawnTasks.clear();
		activeCombatAlerts.clear();
		copAttackers.clear();
		configProvider = null;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		this.configProvider = copLoader.getLoadedProvider();
	}

	@Override
	public void onShutdown() {
		shutdown();
	}

	/**
	 * Finds the {@link CopGroup} that contains the given cop, or {@code null} if the cop is not in any group.
	 */
	private CopGroup findGroupContaining(CopNpc copNpc) {
		for (CopGroup group : groups.values()) {
			if (group.getCops().contains(copNpc)) return group;
		}
		return null;
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

			// check if the player was detained before spawning a new cop
			if (detainmentService.isRestrained(player)) {
				return;
			}

			CopGroup group = groups.get(playerId);
			if (group == null) return;

			List<CopNpc> cops = group.getCops();

			cops.removeIf(cop -> {
				if (cop.isMarkedForRemoval()) {
					cop.destroy(entityMarkManager);
					return true;
				}
				if (!cop.isValid()) {
					// PLAYER-type Citizens NPCs may have a null entity for a tick or two while initializing —
					// keep in list so the count is not artificially low, causing a spawn loop.
					NPC npc = cop.getNpc();
					if (npc.isSpawned() && npc.getEntity() == null) {
						return false;
					}
					cop.destroy(entityMarkManager);
					return true;
				}
				return false;
			});

			int targetCount  = spawnManager.getTargetCopCount(wantedLevel);
			int currentCount = cops.size();
			int tier         = spawnManager.getTierForWantedLevel(wantedLevel);

			// Spawn all missing cops in one pass so a full wipe is recovered in a single interval
			while (currentCount < targetCount && currentCount < configProvider.getMaxCopsPerPlayer()) {
				CopNpc newCop = spawnManager.spawnNearPlayer(player, tier);
				if (newCop == null) break; // no valid location found - stop trying this interval

				newCop.setTargetPlayerId(playerId);

				// New spawns pursue immediately; combatForced flag causes them to enter COMBAT once in range
				newCop.setCombatForced(hasCombatAlert(playerId));
				newCop.transitionTo(CopState.PURSUING);

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

			CopGroup group = groups.get(playerId);
			if (group == null || group.isEmpty()) {
				// Self-cleanup: player is no longer wanted and all cops are gone
				if (!targetingManager.isWanted(playerId)) {
					stopAITask(playerId);
					groups.remove(playerId);
				}
				return;
			}

			List<CopNpc> cops = group.getCops();

			Iterator<CopNpc> iterator = cops.iterator();
			while (iterator.hasNext()) {
				CopNpc cop = iterator.next();

				if (cop.isMarkedForRemoval()) {
					cop.destroy(entityMarkManager);
					iterator.remove();
					continue;
				}

				if (!cop.isValid()) {
					// PLAYER-type Citizens NPCs may have a null entity for a tick or two while initializing —
					// skip this tick instead of destroying so the NPC gets a chance to fully spawn.
					NPC npc = cop.getNpc();
					if (npc.isSpawned() && npc.getEntity() == null) {
						continue;
					}
					cop.destroy(entityMarkManager);
					iterator.remove();
					continue;
				}

				LivingEntity target = resolveTarget(cop, player);
				cop.tick(target);
			}
		}, 0L, configProvider.getAiTickRate());

		aiTasks.put(playerId, task);
	}

	/**
	 * Resolves the current target for a cop. Priority order:
	 * <ol>
	 *   <li>Current wanted target — keep if still valid and wanted.</li>
	 *   <li>Current cop-attacker target — keep if still online and in the attacker registry.</li>
	 *   <li>Nearest wanted player in the world.</li>
	 *   <li>Nearest cop-attacker (combat mode) — someone who previously hit a cop.</li>
	 *   <li>Nearest wanted hostile civilian NPC.</li>
	 *   <li>No valid target — transition to RETURNING.</li>
	 * </ol>
	 *
	 * @param cop the cop NPC
	 * @param defaultTarget the reference player used to find the nearest wanted target
	 *
	 * @return the resolved target (Player or hostile civilian LivingEntity), or null if no target available
	 */
	private LivingEntity resolveTarget(CopNpc cop, Player defaultTarget) {
		// Check if cop is already pursuing a wanted civilian entity
		LivingEntity currentEntity = cop.getTargetEntity();
		if (currentEntity != null) {
			if (currentEntity.isValid() && !currentEntity.isDead()) {
				return currentEntity;
			}
			// Stale entity target — clear and fall through to player search
			cop.setTargetEntity(null);
			cop.setCombatForced(false);
		}

		UUID currentTargetId = cop.getTargetPlayerId();

		if (currentTargetId != null) {
			Player currentTarget = Bukkit.getPlayer(currentTargetId);

			if (currentTarget != null && currentTarget.isOnline() && !currentTarget.isDead() &&
			    !DownedPlayerRegistry.isDowned(currentTargetId)) {
				// Keep if still wanted
				if (targetingManager.isWanted(currentTargetId)) {
					return currentTarget;
				}

				// Keep if this player attacked a cop (combat forced) — pursue even without wanted status
				if (cop.isCombatForced() && copAttackers.contains(currentTargetId)) {
					return currentTarget;
				}
			}

			// Stale target — clear and search for a new one
			cop.setTargetPlayerId(null);
			cop.setCombatForced(false);
		}

		// 1. Find the nearest wanted player (skip downed players)
		Player wanted = targetingManager.findBestTarget(defaultTarget);
		if (wanted != null && !wanted.isDead() && !DownedPlayerRegistry.isDowned(wanted.getUniqueId())) {
			cop.setTargetPlayerId(wanted.getUniqueId());
			cop.setCombatForced(false);
			// Leave combat mode so the cop pursues/cuffs normally instead of attacking
			if (cop.getCurrentState() == CopState.COMBAT) {
				cop.transitionTo(CopState.PURSUING);
			}
			return wanted;
		}

		// 2. Find the nearest player who previously attacked a cop (engage in combat)
		Player attacker = findNearestCopAttacker(cop);
		if (attacker != null) {
			cop.setTargetPlayerId(attacker.getUniqueId());
			cop.setTargetEntity(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT) {
				cop.transitionTo(CopState.COMBAT);
			}
			return attacker;
		}

		// 3. Find the nearest wanted hostile civilian NPC
		LivingEntity wantedCivilian = findNearestWantedCivilian(cop);
		if (wantedCivilian != null) {
			cop.setTargetEntity(wantedCivilian);
			cop.setTargetPlayerId(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT && cop.getCurrentState() != CopState.PURSUING) {
				cop.transitionTo(CopState.PURSUING);
			}
			return wantedCivilian;
		}

		// 4. Check entities that directly attacked this cop (self-defence, lowest priority)
		LivingEntity pendingAttacker = cop.pollNextEntityAttacker();
		if (pendingAttacker != null) {
			cop.setTargetEntity(pendingAttacker);
			cop.setTargetPlayerId(null);
			cop.setCombatForced(true);
			if (cop.getCurrentState() != CopState.COMBAT) {
				cop.transitionTo(CopState.COMBAT);
			}
			return pendingAttacker;
		}

		// No valid target — return to spawn
		cop.setTargetPlayerId(null);
		cop.setTargetEntity(null);
		cop.setCombatForced(false);
		if (cop.getCurrentState() != CopState.RETURNING && cop.getCurrentState() != CopState.IDLE) {
			cop.transitionTo(CopState.RETURNING);
		}
		return null;
	}

	/**
	 * Finds the nearest online player who has previously attacked a cop, relative to the cop's position.
	 *
	 * @param cop the cop NPC
	 *
	 * @return the nearest cop-attacker, or null
	 */
	private Player findNearestCopAttacker(CopNpc cop) {
		if (copAttackers.isEmpty() || !cop.isValid()) return null;

		LivingEntity entity = cop.getEntity();
		if (entity == null) return null;

		Location copLoc   = entity.getLocation();
		Player   best     = null;
		double   bestDist = Double.MAX_VALUE;

		for (UUID attackerId : copAttackers) {
			Player attacker = Bukkit.getPlayer(attackerId);
			if (attacker == null || !attacker.isOnline() || attacker.isDead() ||
			    DownedPlayerRegistry.isDowned(attackerId)) continue;
			if (!attacker.getWorld().equals(copLoc.getWorld())) continue;

			double dist = attacker.getLocation().distanceSquared(copLoc);
			if (dist >= bestDist) continue;

			bestDist = dist;
			best     = attacker;
		}

		return best;
	}

	/**
	 * Finds the nearest active hostile civilian NPC that is wanted by police (i.e. in combat), relative to the cop.
	 *
	 * @param cop the cop NPC
	 *
	 * @return the nearest wanted civilian entity, or null
	 */
	private LivingEntity findNearestWantedCivilian(CopNpc cop) {
		if (civilianNpcRegistry == null || !cop.isValid()) return null;

		LivingEntity copEntity = cop.getEntity();
		if (copEntity == null) return null;

		LivingEntity best     = null;
		double       bestDist = Double.MAX_VALUE;

		for (CivilianNpc civilian : civilianNpcRegistry.getActiveNpcs()) {
			if (!civilian.isHostile() || !civilian.isWantedByPolice() || !civilian.isValid()) continue;

			LivingEntity civEntity = civilian.getEntity();
			if (civEntity == null) continue;
			if (!civEntity.getWorld().equals(copEntity.getWorld())) continue;

			double dist = civEntity.getLocation().distanceSquared(copEntity.getLocation());
			if (dist >= bestDist) continue;

			bestDist = dist;
			best     = civEntity;
		}

		return best;
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
		CopGroup group = groups.remove(playerId);
		if (group == null) return;

		group.destroyAll(entityMarkManager);
	}
}