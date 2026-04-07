package me.luckyraven.copsncrooks.npc.civilian;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.entity.SpawnConfigProvider;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianGroupConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansConfig;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpcFactory;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.item.ItemParser;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Manages the lifecycle and AI ticking of all active civilian NPCs.
 * <p>
 * Call {@link #initialize} once during plugin enable. The service schedules a repeating task that ticks every active
 * civilian and removes dead or marked ones.
 */
@CustomLog
public class CivilianService {

	private final Map<UUID, CivilianNpc>     activeNpcs   = new HashMap<>();
	private final Map<String, CivilianGroup> activeGroups = new HashMap<>();

	@Getter
	private CivilianNpcFactory npcFactory;

	@Getter
	private CivilianSpawnManager spawnManager;

	private EntityMarkManager entityMarkManager;
	@Getter
	private CiviliansConfig   civiliansConfig;
	private JavaPlugin        plugin;
	private boolean           initialized;

	// ── Initialization ────────────────────────────────────────────────────────

	/**
	 * Wires dependencies and starts the AI tick scheduler.
	 *
	 * @param plugin the owning plugin
	 * @param civiliansConfig loaded civilians.yml config
	 * @param entityMarkManager entity classification manager
	 * @param civilianSettings civilian settings
	 * @param itemParser item string parser (nullable — falls back to XMaterial)
	 * @param weaponService gangland weapon registry (nullable — disables weapon assignment)
	 */
	public void initialize(JavaPlugin plugin, CiviliansConfig civiliansConfig, EntityMarkManager entityMarkManager,
	                       IRepository<CivilianSpawner> spawnerRepository, CivilianSettings civilianSettings,
	                       SpawnConfigProvider spawnConfigProvider, @Nullable ItemParser itemParser,
	                       @Nullable WeaponService weaponService) {
		if (initialized) return;

		this.plugin            = plugin;
		this.civiliansConfig   = civiliansConfig;
		this.entityMarkManager = entityMarkManager;
		this.spawnManager      = new CivilianSpawnManager(spawnConfigProvider, spawnerRepository, this,
		                                                  civiliansConfig);
		this.npcFactory        = new CivilianNpcFactory(plugin, entityMarkManager, itemParser, weaponService,
		                                                civilianSettings);

		if (!civilianSettings.isCivilianAiEnabled()) {
			log.info("Civilian AI is disabled — NPCs will not tick.");
			initialized = true;
			return;
		}

		int tickRate = civilianSettings.getCivilianAiTickRate();
		RepeatingTimer tickTimer = new RepeatingTimer(plugin, tickRate, 0, timer -> {
			tickAll();
		});

		tickTimer.start(false);

		int checkInterval = civilianSettings.getCivilianSpawnerCheckInterval();
		RepeatingTimer checkTimer = new RepeatingTimer(plugin, checkInterval, 0, timer -> {
			tickProximitySpawners(civilianSettings);
		});

		checkTimer.start(false);

		initialized = true;
		log.info("CivilianService initialized (tick rate: {} ticks, proximity check: {} ticks).", tickRate,
		         checkInterval);
	}

	// ── NPC registry ─────────────────────────────────────────────────────────

	/**
	 * Registers an already-spawned civilian NPC so it is ticked and cleaned up by this service.
	 */
	public void register(CivilianNpc npc) {
		if (npc == null || !npc.isValid()) return;
		Entity entity = npc.getEntity();
		if (entity == null) return;
		activeNpcs.put(entity.getUniqueId(), npc);
	}

	/**
	 * Registers a civilian group. Members must already be registered via {@link #register}.
	 */
	public void registerGroup(CivilianGroup group) {
		activeGroups.put(group.getGroupId() + "_" + System.nanoTime(), group);
	}

	/**
	 * Returns the active civilian NPC for the given entity UUID, or {@code null}.
	 */
	@Nullable
	public CivilianNpc getNpc(UUID entityId) {
		return activeNpcs.get(entityId);
	}

	/**
	 * Returns all currently active civilian NPCs (unmodifiable view).
	 */
	public Collection<CivilianNpc> getActiveNpcs() {
		return activeNpcs.values();
	}

	/**
	 * Returns all currently active civilian groups (unmodifiable view).
	 */
	public Collection<CivilianGroup> getActiveGroups() {
		return activeGroups.values();
	}

	// ── Group spawning ────────────────────────────────────────────────────────

	/**
	 * Spawns a complete group from civilians.yml at the given location. All members are registered with this service
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
			registerGroup(group);
			// Defer registration by 1 tick so Citizens finishes entity initialisation for all group members
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawned.forEach(this::register), 1L);
			log.debug("Spawned group '{}' with {} members at {}.", groupId, group.getMembers().size(), location);
			return group;
		}

		return null;
	}

	// ── Spawner proximity ─────────────────────────────────────────────────────

	/**
	 * Destroys all active civilian NPCs and clears all registries.
	 */
	public void shutdown() {
		for (CivilianNpc npc : activeNpcs.values()) {
			try {
				npc.destroy(entityMarkManager);
			} catch (Exception e) {
				log.warn("Error destroying civilian NPC during shutdown: {}", e.getMessage());
			}
		}
		activeNpcs.clear();
		activeGroups.clear();
	}

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
		int    maxNpcs            = settings.getCivilianSpawnerMaxNpcs();
		String defaultTypeId      = settings.getCivilianSpawnerDefaultTypeId();

		for (CivilianSpawner spawner : spawnManager.getSpawners()) {
			Location spawnerLoc = spawner.getLocation();
			if (spawnerLoc.getWorld() == null) continue;

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
					boolean hasActiveGroup = activeGroups.values()
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
					long aliveCount = activeNpcs.values()
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

	// ── Shutdown ──────────────────────────────────────────────────────────────

	/**
	 * Marks all civilians spawned from the given spawner for removal. {@link #tickAll} will destroy them on the next
	 * tick.
	 */
	private void despawnFromSpawner(int spawnerId) {
		// Individual NPCs tracked to this spawner
		activeNpcs.values()
				.stream()
				.filter(npc -> Integer.valueOf(spawnerId).equals(npc.getSpawnerId()))
				.forEach(CivilianNpc::markForRemoval);

		// Group members whose group was spawned from this spawner
		activeGroups.values()
				.stream()
				.filter(g -> Integer.valueOf(spawnerId).equals(g.getSpawnerId()))
				.flatMap(g -> g.getMembers()
						.stream())
				.forEach(CivilianNpc::markForRemoval);
	}

	// ── Internal tick ─────────────────────────────────────────────────────────

	private void tickAll() {
		// Tick NPCs; collect dead ones for removal
		activeNpcs.entrySet().removeIf(entry -> {
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
		activeGroups.entrySet().removeIf(entry -> {
			CivilianGroup group = entry.getValue();
			group.pruneDeadMembers();
			return group.isEmpty();
		});
	}
}
