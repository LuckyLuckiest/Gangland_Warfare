package me.luckyraven.copsncrooks.npc.civilian;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.npc.civilian.config.*;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpcFactory;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.entity.EntityMarkManager;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

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
	private final EntityMarkManager    entityMarkManager;
	private final CivilianSettings     civilianSettings;
	private final CivilianNpcFactory   npcFactory;
	private final CivilianSpawnManager spawnManager;
	private final CivilianNpcRegistry  registry;

	@Getter
	private CiviliansConfig civiliansConfig;

	private RepeatingTimer tickTimer;
	private RepeatingTimer checkTimer;

	public CivilianService(JavaPlugin plugin, CiviliansLoader civiliansLoader, EntityMarkManager entityMarkManager,
	                       CivilianSettings civilianSettings, CivilianNpcFactory npcFactory,
	                       CivilianSpawnManager spawnManager, CivilianNpcRegistry registry) {
		this.plugin            = plugin;
		this.civiliansLoader   = civiliansLoader;
		this.entityMarkManager = entityMarkManager;
		this.civilianSettings  = civilianSettings;
		this.npcFactory        = npcFactory;
		this.spawnManager      = spawnManager;
		this.registry          = registry;
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Override
	public void onInitialize(boolean firstLoad) {
		this.civiliansConfig = civiliansLoader.getLoadedConfig();

		if (!civilianSettings.isCivilianAiEnabled()) {
			log.info("Civilian AI is disabled — NPCs will not tick.");
			return;
		}

		int tickRate = civilianSettings.getCivilianAiTickRate();
		this.tickTimer = new RepeatingTimer(plugin, tickRate, 0, timer -> tickAll());
		tickTimer.start(false);

		int checkInterval = civilianSettings.getCivilianSpawnerCheckInterval();
		this.checkTimer = new RepeatingTimer(plugin, checkInterval, 0,
		                                     timer -> tickProximitySpawners(civilianSettings));
		checkTimer.start(false);

		log.info("CivilianService initialized (tick rate: {} ticks, proximity check: {} ticks).", tickRate,
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
				npc.destroy(entityMarkManager);
			} catch (Exception e) {
				log.warn("Error destroying civilian NPC during shutdown: {}", e.getMessage());
			}
		}
		registry.clear();
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
					npc.destroy(entityMarkManager);
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
	}
}
