package me.luckyraven.copsncrooks.npc.civilian;

import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of all active civilian NPCs and groups. Acts as a shared, dependency-free store so that both
 * {@link CivilianService} (write path) and consumers like {@code CopManager} or {@code MoneyDropClassifier} (read path)
 * can access the live NPC set without creating circular dependencies.
 */
public class CivilianNpcRegistry {

	private final Map<UUID, CivilianNpc>     activeNpcs   = new HashMap<>();
	private final Map<String, CivilianGroup> activeGroups = new HashMap<>();

	/**
	 * Registers an already-spawned civilian NPC so it is tracked by the system.
	 */
	public void register(CivilianNpc npc) {
		if (npc == null || !npc.isValid()) return;
		Entity entity = npc.getEntity();
		if (entity == null) return;
		activeNpcs.put(entity.getUniqueId(), npc);
	}

	/**
	 * Registers a civilian group.
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
	 * Returns all currently active civilian NPCs.
	 */
	public Collection<CivilianNpc> getActiveNpcs() {
		return activeNpcs.values();
	}

	/**
	 * Returns all currently active civilian groups.
	 */
	public Collection<CivilianGroup> getActiveGroups() {
		return activeGroups.values();
	}

	/**
	 * Clears all tracked NPCs and groups.
	 */
	public void clear() {
		activeNpcs.clear();
		activeGroups.clear();
	}

	/**
	 * Returns the mutable NPC map — used by {@link CivilianService} for tick-based removal.
	 */
	Map<UUID, CivilianNpc> npcMap() {
		return activeNpcs;
	}

	/**
	 * Returns the mutable group map — used by {@link CivilianService} for tick-based removal.
	 */
	Map<String, CivilianGroup> groupMap() {
		return activeGroups;
	}
}
