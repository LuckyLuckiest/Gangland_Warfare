package me.luckyraven.copsncrooks.npc.civilian;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianGroupConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.EntityMarkerConfig;
import me.luckyraven.item.ItemParser;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
	private EntityMarkManager  entityMarkManager;
	private EntityMarkerConfig markerConfig;
	private boolean            initialized;

	// ── Initialization ────────────────────────────────────────────────────────

	/**
	 * Wires dependencies and starts the AI tick scheduler.
	 *
	 * @param plugin the owning plugin
	 * @param markerConfig loaded entity_marker.yml config
	 * @param entityMarkManager entity classification manager
	 * @param itemParser item string parser (nullable — falls back to XMaterial)
	 * @param weaponService gangland weapon registry (nullable — disables weapon assignment)
	 */
	public void initialize(JavaPlugin plugin, EntityMarkerConfig markerConfig,
	                       EntityMarkManager entityMarkManager,
	                       CivilianSettings civilianSettings,
	                       @Nullable ItemParser itemParser,
	                       @Nullable WeaponService weaponService) {
		if (initialized) return;

		this.markerConfig      = markerConfig;
		this.entityMarkManager = entityMarkManager;
		this.npcFactory        = new CivilianNpcFactory(plugin, entityMarkManager, itemParser,
		                                                weaponService, civilianSettings);

		if (!civilianSettings.isCivilianAiEnabled()) {
			log.info("Civilian AI is disabled — NPCs will not tick.");
			initialized = true;
			return;
		}

		int tickRate = civilianSettings.getCivilianAiTickRate();
		plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, tickRate, tickRate);

		initialized = true;
		log.info("CivilianService initialized (tick rate: {} ticks).", tickRate);
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

	// ── Group spawning ────────────────────────────────────────────────────────

	/**
	 * Spawns a complete group from entity_marker.yml at the given location. All members are registered with this
	 * service and linked to the group.
	 *
	 * @param location the center spawn location for the group
	 * @param groupId the group key as defined in entity_marker.yml
	 */
	public void spawnGroup(Location location, String groupId) {
		CivilianGroupConfig groupConfig = markerConfig.groups().get(groupId);
		if (groupConfig == null) {
			log.warn("Unknown civilian group '{}' — skipping spawn.", groupId);
			return;
		}

		CivilianGroup group = new CivilianGroup(groupId, groupConfig);

		for (Map.Entry<String, Integer> entry : groupConfig.members().entrySet()) {
			String typeId = entry.getKey();
			int    count  = entry.getValue();

			CivilianTypeConfig typeConfig = markerConfig.types().get(typeId);
			if (typeConfig == null) {
				log.warn("Group '{}' references unknown type '{}' — skipping.", groupId, typeId);
				continue;
			}

			for (int i = 0; i < count; i++) {
				CivilianNpc npc = npcFactory.createCivilian(location, typeConfig, groupId, groupConfig);
				if (npc == null) continue;

				npc.setGroup(group);
				group.addMember(npc);
				register(npc);
			}
		}

		if (!group.isEmpty()) {
			registerGroup(group);
			log.debug("Spawned group '{}' with {} members at {}.", groupId, group.getMembers().size(), location);
		}
	}

	// ── Shutdown ──────────────────────────────────────────────────────────────

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

	// ── Internal tick ─────────────────────────────────────────────────────────

	private void tickAll() {
		// Tick NPCs; collect dead ones for removal
		activeNpcs.entrySet().removeIf(entry -> {
			CivilianNpc npc = entry.getValue();
			if (npc.isMarkedForRemoval() || !npc.isValid()) {
				npc.destroy(entityMarkManager);
				return true;
			}
			npc.tick();
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
