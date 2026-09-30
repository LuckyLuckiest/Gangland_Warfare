package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.Getter;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StuckSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.wanted.Wanted;

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
	private final NpcMarkManager        markManager;
	private final DetainmentService     detainmentService;
	private final Map<UUID, CopGroup>   groups;
	/** Players whose current wanted clear has already been announced with Stand_Down; reset by the next wanted start. */
	private final java.util.Set<UUID>    stoodDown = new java.util.HashSet<>();
	private final Map<UUID, BukkitTask> aiTasks;
	private final Map<UUID, BukkitTask> spawnTasks;
	private final Set<UUID>             activeCombatAlerts;
	private final Set<UUID>             copAttackers;
	private final CivilianNpcRegistry   civilianNpcRegistry;
	private final CopRadio              copRadio;
	/**
	 * Radio calls heard since the last AI tick; answered after the tick's cop loop so a listener never changes a cop
	 * list that is being iterated.
	 */
	private final Deque<RadioCall>      pendingCalls = new ArrayDeque<>();
	private       CopConfigProvider     configProvider;

	public CopManager(JavaPlugin plugin, CopSpawnManager spawnManager, TargetingManager targetingManager,
	                  CopLoader copLoader, NpcMarkManager markManager,
	                  DetainmentService detainmentService, CivilianNpcRegistry civilianNpcRegistry,
	                  CopRadio copRadio) {
		this.plugin            = plugin;
		this.spawnManager      = spawnManager;
		this.targetingManager  = targetingManager;
		this.copLoader         = copLoader;
		this.configProvider    = copLoader.getLoadedProvider();
		this.markManager       = markManager;
		this.detainmentService = detainmentService;
		this.copRadio          = copRadio;

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
		stoodDown.remove(playerId);
		// The crime scene is known: the group's squad starts from where the player is now. Dispatch announces a new
		// hunt, or one whose trail went cold, before the squad hears of it.
		CopGroup existing = groups.get(playerId);
		CopGroup group    = groups.computeIfAbsent(playerId, this::newGroup);
		if (existing == null || !group.getSquad().hasFreshSighting()) {
			copRadio.dispatch(group, player, "Dispatch_Wanted", wanted.getLevel(), tierNameFor(wanted.getLevel()));
		}
		group.getSquad().reportSighting(player.getLocation());

		// A new wanted start is a new episode: pull the group's returning cops back into the hunt instead of letting
		// them walk home for up to Return.Max_Ticks. "Never give up while wanted" outranks the D1 re-engage rule,
		// which only guards against a per-tick bounce inside a single episode.
		for (CopNpc cop : group.getCops()) {
			if (!cop.isValid() || cop.getCurrentState() != CopState.RETURNING) continue;
			cop.setTargetPlayerId(playerId);
			cop.transitionTo(CopState.PURSUING);
		}

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
		if (group != null) {
			group.clearCombatAlert();
			pendingCalls.removeIf(call -> call.group() == group);
		}
		if (group == null || group.isEmpty()) {
			stopAITask(playerId);
			groups.remove(playerId);
			return;
		}

		// WantedEndEvent and the level change to 0 both land here for one clear: announce it once per episode.
		if (stoodDown.add(playerId)) copRadio.sayFromLeader(group, "Stand_Down");

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

		// Hitting a cop gives the group's target away: the whole squad learns where he is, and he is resisting
		if (attacker.getUniqueId().equals(group.getTargetPlayerId())) {
			group.getSquad().reportSighting(attacker.getLocation());
			group.escalate(attacker.getUniqueId());
		}

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
	 * The cop whose entity is {@code entity}, even while that entity is dying: {@link #findCopByEntity} requires a
	 * valid entity, and an entity is no longer valid during its {@code EntityDeathEvent}.
	 */
	public @Nullable CopNpc findDyingCop(Entity entity) {
		for (CopGroup group : groups.values()) {
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops()) {
					Entity copEntity = cop.getNpc().getEntity();
					if (copEntity != null && copEntity.getUniqueId().equals(entity.getUniqueId())) return cop;
				}
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
		pendingCalls.clear();
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
	 * The group hunting {@code playerId}, or {@code null}. Package-private test seam.
	 */
	CopGroup groupFor(UUID playerId) {
		return groups.get(playerId);
	}

	/** A new group whose squads speak on the police radio and queue its calls here. */
	private CopGroup newGroup(UUID playerId) {
		CopGroup group = new CopGroup(playerId);
		group.setListener(copRadio.listenerFor(group, pendingCalls::add));
		return group;
	}

	private String tierNameFor(int wantedLevel) {
		return CopRadio.tierName(configProvider.getTierConfig(spawnManager.getTierForWantedLevel(wantedLevel)));
	}

	private BackupSettings backupSettings() {
		BackupSettings backup = configProvider.getBackupSettings();
		return backup != null ? backup : BackupSettings.DEFAULT;
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

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> spawnTick(playerId, wanted), 20L,
		                                                     configProvider.getSpawnCheckRate());

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

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> aiTick(playerId), 0L,
		                                                     configProvider.getAiTickRate());

		aiTasks.put(playerId, task);
	}

	/** One spawn-task run for {@code playerId}'s group. Package-private test seam. */
	void spawnTick(UUID playerId, Wanted wanted) {
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
		long         now  = copRadio.now();

		cops.removeIf(cop -> {
			if (cop.isMarkedForRemoval() || recycles(cop, group, player, now)) {
				group.release(cop, markManager);
				return true;
			}
			if (!cop.isValid()) {
				// PLAYER-type Citizens NPCs may have a null entity for a tick or two while initializing —
				// keep in list so the count is not artificially low, causing a spawn loop.
				NPC npc = cop.getNpc();
				if (npc.isSpawned() && npc.getEntity() == null) {
					return false;
				}
				group.release(cop, markManager);
				return true;
			}
			return false;
		});

		BackupSettings backup     = backupSettings();
		int            tier       = spawnManager.getTierForWantedLevel(wantedLevel);
		CopTierConfig  tierConfig = configProvider.getTierConfig(tier);

		if (tierConfig != null) group.getSquad().setFormationArc(tierConfig.tactics().formationArc());
		group.setLevel(wantedLevel);
		group.setTierName(CopRadio.tierName(tierConfig));
		if (group.getLastTier() > 0 && tier > group.getLastTier()) {
			copRadio.dispatch(group, player, "Escalate", wantedLevel, group.getTierName());
		}
		group.setLastTier(tier);

		int targetCount  = Math.min(spawnManager.getTargetCopCount(wantedLevel) + group.backupExtra(now, backup),
		                            configProvider.getMaxCopsPerPlayer());
		// A RETURNING cop beyond the pursuit range cannot engage: it is walking home, not part of this hunt's count.
		double maxDist = configProvider.getPursuitMaxDistance();
		int currentCount = (int) cops.stream().filter(c -> !isStrandedReturning(c, player, maxDist)).count();

		// Spawn all missing cops in one pass so a full wipe is recovered in a single interval
		while (currentCount < targetCount) {
			CopNpc newCop = spawnManager.spawnNearPlayer(player, tier, loc -> !group.isAvoided(loc, now));
			if (newCop == null) break; // no valid location found - stop trying this interval

			newCop.setTargetPlayerId(playerId);

			// New spawns pursue immediately; combatForced flag causes them to enter COMBAT once in range
			newCop.setCombatForced(hasCombatAlert(playerId) || group.isCombatAlert());
			newCop.transitionTo(CopState.PURSUING);

			group.add(newCop);
			currentCount++;
		}

		group.consumeBackupExpiry(now, backup);
		if (group.getPendingRelease() > 0) releaseSurplus(group, targetCount);
		group.pruneAttackerSquads();
	}

	/**
	 * A cop hunting the group's suspect that has found no way to him for {@code Cops.Stuck.Recycle_Seconds}
	 * ({@link CopNpc#millisUnreachable()}) and that no one is looking at is taken off the map; its spawner is skipped
	 * for {@code Avoid_Spawner_Seconds} so the replacement spawned in the same run comes from elsewhere.
	 */
	private boolean recycles(CopNpc cop, CopGroup group, Player player, long now) {
		StuckSettings stuck = configProvider.getStuckSettings();
		CopState      state = cop.getCurrentState();
		LivingEntity  body  = cop.getEntity();
		if (!stuck.enabled() || body == null || (state != CopState.PURSUING && state != CopState.COMBAT)) return false;
		if (!player.getUniqueId().equals(cop.getTargetPlayerId())) return false;
		if (cop.millisUnreachable() < stuck.recycleSeconds() * 1000L) return false;

		Location at = body.getLocation();
		if (at.getWorld() == player.getWorld()) {
			// a melee cop knocked off its surround slot keeps its clock running though it can hit him
			if (at.distance(player.getLocation()) <= configProvider.getMeleeProfile().reach()) return false;
			if (inView(player, body, stuck.viewDistance())) return false;
		}
		if (spawnManager.isVisibleToOtherPlayers(at, player)) return false;

		Location origin = cop.getSpawnLocation();
		if (origin != null && stuck.avoidSpawnerSeconds() > 0)
			group.avoid(origin, now + stuck.avoidSpawnerSeconds() * 1000L);
		return true;
	}

	/**
	 * {@code body} is in front of the suspect's eyes (within 60 degrees of where he looks, up and down included) with
	 * a clear line within {@code maxDistance}. A bare line of sight would keep every cop on a ledge above his head.
	 */
	private static boolean inView(Player player, LivingEntity body, double maxDistance) {
		Location eye    = player.getEyeLocation();
		Vector   toward = body.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(eye.toVector());
		if (toward.lengthSquared() > maxDistance * maxDistance) return false;
		return eye.getDirection().angle(toward) < Math.PI / 3 && player.hasLineOfSight(body);
	}

	private static boolean isStrandedReturning(CopNpc cop, Player player, double maxDist) {
		if (cop.getCurrentState() != CopState.RETURNING || cop.getEntity() == null) return false;
		Location at = cop.getEntity().getLocation();
		return at.getWorld() != player.getWorld() || at.distanceSquared(player.getLocation()) > maxDist * maxDist;
	}

	/**
	 * Sends the newest free cops home once a backup ran out, until the group is back to {@code targetCount}. A cop in
	 * a fight, cuffing or guarding is never sent; what cannot go this run is retried on the next.
	 */
	private void releaseSurplus(CopGroup group, int targetCount) {
		List<CopNpc> live = new ArrayList<>();
		synchronized (group.getCops()) {
			for (CopNpc cop : group.getCops())
				if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) live.add(cop);
		}

		int surplus = live.size() - targetCount;
		if (surplus <= 0) {
			group.setPendingRelease(0);
			return;
		}

		for (int i = live.size() - 1; i >= 0 && surplus > 0 && group.getPendingRelease() > 0; i--) {
			CopNpc   cop   = live.get(i);
			CopState state = cop.getCurrentState();
			if (state != CopState.PURSUING && state != CopState.IDLE) continue;

			cop.transitionTo(CopState.RETURNING);
			group.setPendingRelease(group.getPendingRelease() - 1);
			surplus--;
		}
	}

	/** One AI-task run for {@code playerId}'s group. Package-private test seam. */
	void aiTick(UUID playerId) {
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
			drainRadioCalls();
			return;
		}

		List<CopNpc> cops = group.getCops();

		Iterator<CopNpc> iterator = cops.iterator();
		while (iterator.hasNext()) {
			CopNpc cop = iterator.next();

			if (cop.isMarkedForRemoval()) {
				group.release(cop, markManager);
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
				group.release(cop, markManager);
				iterator.remove();
				continue;
			}

			LivingEntity target = resolveTarget(cop, player);
			cop.tick(target);
		}

		if (group.pollResisting()) {
			copRadio.sayFromLeader(group, "Resisting");
			fightResisting(group);
		}
		drainRadioCalls();
	}

	/** The suspect resisted: every cop still after him turns from cuffing to fighting. */
	private void fightResisting(CopGroup group) {
		for (CopNpc cop : new ArrayList<>(group.getCops())) {
			if (!cop.isValid() || !group.getTargetPlayerId().equals(cop.getTargetPlayerId())) continue;
			CopState state = cop.getCurrentState();
			if (state == CopState.GUARDING || state == CopState.RETURNING) continue;

			cop.setCombatForced(true);
			cop.transitionTo(CopState.COMBAT);
		}
	}

	/**
	 * Answers the radio calls heard this tick: up to {@code Responder_Max} cops of other groups within {@code Range} of
	 * a call, that are walking home, idle or chasing a civilian, join the calling squad. A cop cuffing, guarding or hunting a player is
	 * never pulled; nor is anyone sent after a suspect already restrained.
	 */
	void drainRadioCalls() {
		RadioCall call;
		while ((call = pendingCalls.poll()) != null) {
			CopGroup group = call.group();
			if (groups.get(group.getTargetPlayerId()) != group) continue;

			LivingEntity hunted = copRadio.huntedOf(group, call.squad());
			if (hunted == null || !hunted.isValid() || hunted.isDead()) continue;
			if (hunted instanceof Player huntedPlayer && detainmentService.isRestrained(huntedPlayer)) continue;

			var settings = configProvider.getRadioSettings();
			int max      = settings != null ? settings.responderMax() : 0;
			if (max <= 0) continue;
			double range = settings.range();

			int live = 0;
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops())
					if (cop.isValid() && cop.getCurrentState() != CopState.RETURNING) live++;
			}
			int room = configProvider.getMaxCopsPerPlayer() - live;
			if (room <= 0) continue;

			for (CopNpc responder : responders(call, range, Math.min(max, room))) {
				CopGroup from = responder.getGroup();
				if (from != group) {
					if (from != null) from.detach(responder);
					group.add(responder);
				}

				// An entity target outranks everything in resolveTarget: drop the civilian first
				responder.setTargetEntity(null);
				UUID groupTarget = group.getTargetPlayerId();
				if (groupTarget.equals(hunted.getUniqueId())) {
					responder.setTargetPlayerId(groupTarget);
					responder.setCombatForced(hasCombatAlert(groupTarget) || group.isCombatAlert());
					responder.transitionTo(CopState.PURSUING);
				} else {
					if (hunted instanceof Player attacker && !NpcSupport.isNpc(attacker)) {
						responder.setTargetPlayerId(attacker.getUniqueId());
					} else {
						responder.setTargetEntity(hunted);
					}
					responder.setCombatForced(true);
					responder.transitionTo(CopState.COMBAT);
				}
				copRadio.respond(group, call.squad(), responder);
			}
		}
	}

	/**
	 * The nearest cops of other groups free to answer {@code call}, at most {@code limit}. The calling group's own cops
	 * are never pulled: one walking home was released after a backup or rotated out, and pulling it back would undo
	 * that (a released backup never leaving, or the D1 PURSUING/RETURNING bounce).
	 */
	private List<CopNpc> responders(RadioCall call, double range, int limit) {
		Location     origin     = call.origin();
		NpcSquad     squad      = call.squad();
		List<CopNpc> candidates = new ArrayList<>();
		Map<CopNpc, Double> distances = new HashMap<>();

		for (CopGroup group : groups.values()) {
			if (group == call.group()) continue;
			synchronized (group.getCops()) {
				for (CopNpc cop : group.getCops()) {
					if (!cop.isValid() || cop.isMarkedForRemoval() || squad.members().contains(cop)) continue;
					if (!isFreeToRespond(cop)) continue;

					LivingEntity entity = cop.getEntity();
					if (entity == null || origin.getWorld() == null ||
					    !origin.getWorld().equals(entity.getWorld())) continue;
					double distSq = entity.getLocation().distanceSquared(origin);
					if (distSq > range * range) continue;

					candidates.add(cop);
					distances.put(cop, distSq);
				}
			}
		}

		candidates.sort(Comparator.comparingDouble(distances::get));
		return candidates.subList(0, Math.min(limit, candidates.size()));
	}

	/** Walking home, idle, or chasing a civilian; never cuffing, guarding or hunting a player. */
	private static boolean isFreeToRespond(CopNpc cop) {
		return switch (cop.getCurrentState()) {
			case RETURNING, IDLE -> true;
			case PURSUING, COMBAT -> cop.getTargetEntity() != null && cop.getTargetPlayerId() == null;
			default -> false;
		};
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

		group.destroyAll(markManager);
	}
}