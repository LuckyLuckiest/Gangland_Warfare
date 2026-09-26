package org.luckyraven.gangland.civilians.npc;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.config.*;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawner;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.timer.RepeatingTimer;

import java.util.*;

/**
 * Manages the lifecycle and AI ticking of all active civilian NPCs.
 * <p>
 * Runtime setup (config reading, timer start) happens in {@link #onInitialize(boolean)}, called by the bean lifecycle
 * after all beans are constructed.
 */
@CustomLog
public class CivilianService implements BeanLifecycle {

	private final JavaPlugin           plugin;
	private final CiviliansLoader      civiliansLoader;
	private final NpcMarkManager       markManager;
	private final CivilianSettings     civilianSettings;
	private final CivilianNpcFactory   npcFactory;
	private final CivilianSpawnManager spawnManager;
	private final CivilianNpcRegistry  registry;
	/**
	 * Combat squads, one per faction and target (player or entity uuid): created on the first hit, dropped when empty
	 * or when their target dies or goes down.
	 */
	private final Map<SquadKey, NpcSquad> squads = new HashMap<>();

	@Getter
	private CiviliansConfig civiliansConfig;

	private RepeatingTimer tickTimer;
	private RepeatingTimer checkTimer;

	public CivilianService(JavaPlugin plugin, CiviliansLoader civiliansLoader, NpcMarkManager markManager,
	                       CivilianSettings civilianSettings, CivilianNpcFactory npcFactory,
	                       CivilianSpawnManager spawnManager, CivilianNpcRegistry registry) {
		this.plugin           = plugin;
		this.civiliansLoader  = civiliansLoader;
		this.markManager      = markManager;
		this.civilianSettings = civilianSettings;
		this.npcFactory       = npcFactory;
		this.spawnManager     = spawnManager;
		this.registry         = registry;
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Override
	public void onInitialize(boolean firstLoad) {
		this.civiliansConfig = civiliansLoader.getLoadedConfig();

		// T-H4: every civilian spawn task goes through Citizens (CivilianNpcFactory#createCivilian calls
		// CitizensAPI.getNPCRegistry()) — never start the tick/proximity-check timers without it. CiviliansModule
		// reports npc.citizens.missing once at module-enable time; this guard is what actually prevents the tasks.
		if (!NpcSupport.available()) {
			log.debug("Citizens is not installed or not enabled — civilian NPCs will not tick.");
			return;
		}

		if (!civilianSettings.isCivilianAiEnabled()) {
			log.debug("Civilian AI is disabled — NPCs will not tick.");
			return;
		}

		int tickRate = civilianSettings.getCivilianAiTickRate();
		this.tickTimer = new RepeatingTimer(plugin, tickRate, 0, timer -> tickAll());
		tickTimer.start(false);

		int checkInterval = civilianSettings.getCivilianSpawnerCheckInterval();
		this.checkTimer = new RepeatingTimer(plugin, checkInterval, 0,
		                                     timer -> tickProximitySpawners(civilianSettings));
		checkTimer.start(false);

		log.debug("CivilianService initialized (tick rate: {} ticks, proximity check: {} ticks).", tickRate,
		          checkInterval);
	}

	@Override
	public void onPreClear() {
		if (tickTimer != null) tickTimer.stop();
		if (checkTimer != null) checkTimer.stop();
		shutdown();
	}

	@Override
	public void onClear() {
		civiliansConfig = null;
		tickTimer       = null;
		checkTimer      = null;
	}

	@Override
	public void onShutdown() {
		shutdown();
	}

	// ── Registry delegates ───────────────────────────────────────────────────

	public void register(CivilianNpc npc) {
		registry.register(npc);
	}

	@Nullable
	public CivilianNpc getNpc(UUID entityId) {
		return registry.getNpc(entityId);
	}

	public Collection<CivilianNpc> getActiveNpcs() {
		return registry.getActiveNpcs();
	}

	public Collection<CivilianGroup> getActiveGroups() {
		return registry.getActiveGroups();
	}

	// ── Combat squads ─────────────────────────────────────────────────────────

	/**
	 * A hostile civilian was hit: it joins its faction's squad against the attacker and reports where the attacker is.
	 * Every other active, combat-enabled hostile civilian of the same faction within its own
	 * {@code AI.Combat.Alert_Range} of the victim that is not already fighting someone else takes the attacker as its
	 * target, enters {@link CivilianState#COMBAT} and joins the squad. One hop: joining does not alert anyone else; a
	 * later hit on any member alerts that member's neighbours.
	 *
	 * @param victim the civilian that was hit, already targeting {@code attacker}
	 * @param attacker the player or entity that hit it
	 * @param playerAttacker whether {@code attacker} is a real player (allies target it by player id) rather than an
	 * 		NPC or mob (allies put it at the front of their entity target queue)
	 */
	public void alertFaction(CivilianNpc victim, LivingEntity attacker, boolean playerAttacker) {
		UUID     attackerId = attacker.getUniqueId();
		String   faction    = victim.getTypeConfig().faction();
		NpcSquad squad      = squads.computeIfAbsent(new SquadKey(faction, attackerId), key -> new NpcSquad());

		victim.joinSquad(squad, attackerId);
		squad.reportSighting(attacker.getLocation());

		LivingEntity victimEntity = victim.getEntity();
		if (victimEntity == null) return;

		// ponytail: scans every active civilian per hit; add a spatial index if active civilians reach the hundreds
		for (CivilianNpc ally : registry.getActiveNpcs()) {
			if (ally == victim || !ally.isValid() || ally.isMarkedForRemoval()) continue;
			if (ally.getEntity() != null && attackerId.equals(ally.getEntity().getUniqueId())) continue;
			if (!ally.isHostile() || !ally.getTypeConfig().ai().combatEnabled()) continue;
			if (!faction.equals(ally.getTypeConfig().faction())) continue;
			if (ally.distanceTo(victimEntity) > ally.getTypeConfig().ai().alertRange()) continue;
			if (isFightingAnother(ally, attackerId)) continue;

			if (playerAttacker) {
				ally.setTargetPlayerId(attackerId);
			} else {
				ally.addEntityTargetToFront(attacker);
			}
			ally.joinSquad(squad, attackerId);
			if (ally.getCurrentState() != CivilianState.COMBAT) {
				ally.transitionTo(CivilianState.COMBAT);
			}
		}
	}

	/**
	 * Forgets every faction's squad hunting {@code targetId} (the target died or went down).
	 */
	public void dropSquads(UUID targetId) {
		squads.keySet().removeIf(key -> key.targetId().equals(targetId));
	}

	/**
	 * Whether {@code npc} is already in combat against a target other than {@code targetId}.
	 */
	private static boolean isFightingAnother(CivilianNpc npc, UUID targetId) {
		if (npc.getCurrentState() != CivilianState.COMBAT) return false;

		LivingEntity entityTarget = npc.getTargetEntity();
		UUID         current      = entityTarget != null ? entityTarget.getUniqueId() : npc.getTargetPlayerId();
		return current != null && !current.equals(targetId);
	}

	/**
	 * One faction's hunt for one target.
	 */
	private record SquadKey(String faction, UUID targetId) {
	}

	// ── Group spawning ────────────────────────────────────────────────────────

	/**
	 * Spawns a complete group from civilians.yml at the given location. All members are registered with the registry
	 * and linked to the group.
	 *
	 * @param location the center spawn location for the group
	 * @param groupId the group key as defined in civilians.yml
	 */
	@Nullable
	public CivilianGroup spawnGroup(Location location, String groupId) {
		CivilianGroupConfig groupConfig = civiliansConfig.groups().get(groupId);
		if (groupConfig == null) {
			log.warn("Unknown civilian group '{}' — skipping spawn.", groupId);
			return null;
		}

		CivilianGroup group = new CivilianGroup(groupId, groupConfig);

		List<CivilianNpc> spawned = new ArrayList<>();

		for (Map.Entry<String, Integer> entry : groupConfig.members().entrySet()) {
			String typeId = entry.getKey();
			int    count  = entry.getValue();

			CivilianTypeConfig typeConfig = civiliansConfig.types().get(typeId);
			if (typeConfig == null) {
				log.warn("Group '{}' references unknown type '{}' — skipping.", groupId, typeId);
				continue;
			}

			for (int i = 0; i < count; i++) {
				CivilianNpc npc = npcFactory.createCivilian(location, typeConfig, groupId, groupConfig);
				if (npc == null) continue;

				npc.setGroup(group);
				group.addMember(npc);
				spawned.add(npc);
			}
		}

		if (!group.isEmpty()) {
			registry.registerGroup(group);
			// Defer registration by 1 tick so Citizens finishes entity initialisation for all group members
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawned.forEach(registry::register), 1L);
			log.debug("Spawned group '{}' with {} members at {}.", groupId, group.getMembers().size(), location);
			return group;
		}

		return null;
	}

	// ── Shutdown ──────────────────────────────────────────────────────────────

	/**
	 * Destroys all active civilian NPCs and clears all registries.
	 */
	public void shutdown() {
		for (CivilianNpc npc : registry.getActiveNpcs()) {
			try {
				npc.destroy(markManager::removeMark);
			} catch (Exception e) {
				log.warn("Error destroying civilian NPC during shutdown: {}", e.getMessage());
			}
		}
		registry.clear();
		squads.clear();
	}

	// ── Proximity spawners ────────────────────────────────────────────────────

	/**
	 * Checks all registered spawners each interval. Spawns civilians when a player enters the activation radius and
	 * despawns them when all players leave the despawn radius.
	 */
	private void tickProximitySpawners(CivilianSettings settings) {
		Collection<? extends Player> players = Bukkit.getOnlinePlayers();
		if (players.isEmpty()) {
			spawnManager.getSpawners().forEach(spawner -> despawnFromSpawner(spawner.getId()));
			return;
		}

		double activationRadiusSq = Math.pow(settings.getCivilianSpawnerActivationRadius(), 2);
		double despawnRadiusSq    = Math.pow(settings.getCivilianSpawnerDespawnRadius(), 2);
		double hardLeashSq        = Math.pow(settings.getCivilianSpawnerHardLeashRadius(), 2);
		int    maxNpcs            = settings.getCivilianSpawnerMaxNpcs();
		String defaultTypeId      = settings.getCivilianSpawnerDefaultTypeId();

		for (CivilianSpawner spawner : spawnManager.getSpawners()) {
			Location spawnerLoc = spawner.getLocation();
			if (spawnerLoc.getWorld() == null) continue;

			// Hard leash — mark any NPC that wandered past the leash for removal so its cap slot frees.
			markStrayNpcsForRemoval(spawner.getId(), spawnerLoc, hardLeashSq);

			boolean anyWithinActivation = false;
			boolean anyWithinDespawn    = false;

			for (Player player : players) {
				if (!player.getWorld().equals(spawnerLoc.getWorld())) continue;
				double distSq = player.getLocation().distanceSquared(spawnerLoc);
				if (distSq <= activationRadiusSq) {
					anyWithinActivation = true;
					break;
				}
				if (distSq <= despawnRadiusSq) {
					anyWithinDespawn = true;
				}
			}

			if (anyWithinActivation) {
				if (spawner.getGroupId() != null) {
					// Group spawner — spawn one group if none currently alive from this spawner
					boolean hasActiveGroup = registry.getActiveGroups()
							.stream()
							.anyMatch(g -> Integer.valueOf(spawner.getId()).equals(g.getSpawnerId()) && !g.isEmpty());

					if (!hasActiveGroup) {
						CivilianGroup group = spawnGroup(spawnerLoc, spawner.getGroupId());
						if (group != null) {
							group.setSpawnerId(spawner.getId());
						}
					}
				} else {
					// Individual NPC spawner — fill up to maxNpcs
					long aliveCount = registry.getActiveNpcs()
							.stream()
							.filter(npc -> Integer.valueOf(spawner.getId()).equals(npc.getSpawnerId()))
							.filter(npc -> npc.isValid() && !npc.isMarkedForRemoval())
							.count();

					if (aliveCount < maxNpcs) {
						String typeId = spawner.getTypeId() != null ? spawner.getTypeId() : defaultTypeId;
						if (typeId == null || typeId.isBlank()) continue;

						CivilianNpc npc = spawnManager.spawnCivilian(spawnerLoc, typeId);
						if (npc != null) {
							npc.setSpawnerId(spawner.getId());
						}
					}
				}
			} else if (!anyWithinDespawn) {
				despawnFromSpawner(spawner.getId());
			}
		}
	}

	/**
	 * Marks any civilian whose distance from {@code spawnerLoc} exceeds the hard-leash radius for removal, so the
	 * per-spawner cap frees up and a replacement can spawn.
	 */
	private void markStrayNpcsForRemoval(int spawnerId, Location spawnerLoc, double hardLeashSq) {
		Integer id = spawnerId;

		for (CivilianNpc npc : registry.getActiveNpcs()) {
			if (!id.equals(npc.getSpawnerId())) continue;
			if (npc.isMarkedForRemoval() || !npc.isValid()) continue;

			Location npcLoc = npc.getEntity().getLocation();
			if (!Objects.requireNonNull(npcLoc.getWorld()).equals(spawnerLoc.getWorld())) {
				npc.markForRemoval();
				continue;
			}

			if (npcLoc.distanceSquared(spawnerLoc) > hardLeashSq) {
				npc.markForRemoval();
			}
		}
	}

	/**
	 * Marks all civilians spawned from the given spawner for removal. {@link #tickAll} will destroy them on the next
	 * tick.
	 */
	private void despawnFromSpawner(int spawnerId) {
		// Individual NPCs tracked to this spawner
		registry.getActiveNpcs()
				.stream()
				.filter(npc -> Integer.valueOf(spawnerId).equals(npc.getSpawnerId()))
				.forEach(CivilianNpc::markForRemoval);

		// Group members whose group was spawned from this spawner
		registry.getActiveGroups()
				.stream()
				.filter(g -> Integer.valueOf(spawnerId).equals(g.getSpawnerId()))
				.flatMap(g -> g.getMembers()
						.stream())
				.forEach(CivilianNpc::markForRemoval);
	}

	// ── Internal tick ─────────────────────────────────────────────────────────

	private void tickAll() {
		// Tick NPCs; collect dead ones for removal
		registry.npcMap().entrySet().removeIf(entry -> {
			CivilianNpc npc = entry.getValue();
			if (npc.isMarkedForRemoval() || !npc.isValid()) {
				try {
					npc.destroy(markManager::removeMark);
				} catch (Exception e) {
					log.warn("Error destroying civilian NPC during tick: {}", e.getMessage());
				}
				return true;
			}
			try {
				npc.tick();
			} catch (Exception e) {
				log.warn("Civilian NPC tick threw exception, marking for removal: {}", e.getMessage());
				npc.markForRemoval();
			}
			return false;
		});

		// Clean empty groups
		registry.groupMap().entrySet().removeIf(entry -> {
			CivilianGroup group = entry.getValue();
			group.pruneDeadMembers();
			return group.isEmpty();
		});

		// Squads every member has left (gave up, died, despawned)
		squads.values().removeIf(NpcSquad::isEmpty);
	}
}
