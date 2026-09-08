package org.luckyraven.gangland.copsncrooks.npc.turf;

import lombok.CustomLog;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.listener.turf.TurfPowerupChunkLoadListener;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.turf.config.TurfPowerupSettings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Supplier;

/**
 * Owns the per-turf Quartermaster NPCs. One {@link TurfPowerupNpc} per turf at most, keyed by turf id. Each NPC is a
 * {@link CivilianNpc} of the configured type — the civilian system handles AI / health / model / equipment entirely.
 * This manager just owns the spawn / lookup / engage / disengage lifecycle and tags the underlying Citizens NPC with
 * the turf-id metadata so the right-click listener can route clicks back into the panel flow.
 */
@CustomLog
public final class TurfPowerupManager implements BeanLifecycle {

	private final JavaPlugin                   plugin;
	private final IRepository<TurfPowerupData> repository;
	private final TurfPowerupSettings          settings;
	private final CivilianSpawnManager         civilianSpawnManager;

	private final Map<Integer, TurfPowerupNpc>    byTurfId = new HashMap<>();
	/**
	 * Quartermaster data whose chunk wasn't loaded at spawn-attempt time. Drained by
	 * {@link #onChunkLoaded(org.bukkit.World, int, int)} as players approach and Bukkit naturally loads chunks.
	 */
	private final java.util.List<TurfPowerupData> pending  = new java.util.ArrayList<>();

	public TurfPowerupManager(JavaPlugin plugin,
	                          IRepository<TurfPowerupData> repository,
	                          TurfPowerupSettings settings,
	                          CivilianSpawnManager civilianSpawnManager) {
		this.plugin               = plugin;
		this.repository           = repository;
		this.settings             = settings;
		this.civilianSpawnManager = civilianSpawnManager;

		this.repository.setDataSupplier(this::snapshotData);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		// Defer spawn so worlds + the civilian service are fully online before Citizens drops the NPCs in.
		Bukkit.getScheduler().runTaskLater(plugin, this::spawnAllFromRepository, 60L);
	}

	@Override
	public void onPreClear() {
		despawnAll();
	}

	@Override
	public void onClear() {
		byTurfId.clear();
		pending.clear();
	}

	@Override
	public void onShutdown() {
		despawnAll();
		byTurfId.clear();
		pending.clear();
	}

	public Collection<TurfPowerupData> snapshotData() {
		List<TurfPowerupData> snap = new ArrayList<>(byTurfId.size());
		for (TurfPowerupNpc npc : byTurfId.values()) snap.add(npc.getData());
		return snap;
	}

	/**
	 * Place / update the powerup NPC for {@code turfId} at {@code location}. Despawns any previous NPC for the same
	 * turf, persists the new data, and spawns a fresh Quartermaster civilian. The display name passed at admin time
	 * wins; if null the configured civilian-type's display name is used.
	 */
	public TurfPowerupNpc place(int turfId, Location location, @Nullable String displayName) {
		remove(turfId);
		TurfPowerupData data = new TurfPowerupData(turfId, location, displayName);
		repository.save(data);
		return spawn(data);
	}

	public void remove(int turfId) {
		TurfPowerupNpc existing = byTurfId.remove(turfId);
		if (existing != null) existing.destroy();

		repository.loadAll()
				.stream()
				.filter(d -> d.getTurfId() == turfId)
				.findFirst()
				.ifPresent(repository::delete);
	}

	@Nullable
	public TurfPowerupNpc get(int turfId) {
		return byTurfId.get(turfId);
	}

	@Nullable
	public TurfPowerupNpc getByEntity(Entity entity) {
		if (entity == null) return null;
		NPC citizensNpc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (citizensNpc == null) return null;
		if (!citizensNpc.data().has(TurfPowerupNpc.METADATA_TURF_ID)) return null;
		Object raw = citizensNpc.data().get(TurfPowerupNpc.METADATA_TURF_ID);
		if (!(raw instanceof Number n)) return null;
		return byTurfId.get(n.intValue());
	}

	/**
	 * Make this turf's Quartermaster hostile to whichever players the supplier names. No-op if the turf has no
	 * Quartermaster set up.
	 */
	public void engage(int turfId, Supplier<Set<UUID>> attackers) {
		TurfPowerupNpc npc = byTurfId.get(turfId);
		if (npc == null) return;
		npc.engage(attackers, 32.0);
	}

	public void disengage(int turfId) {
		TurfPowerupNpc npc = byTurfId.get(turfId);
		if (npc == null) return;
		npc.disengage();
	}

	/**
	 * Called by {@link TurfPowerupChunkLoadListener} when Bukkit loads a chunk. Scans the pending-spawn queue for any
	 * Quartermaster whose anchor sits in that chunk and spawns it now. Once spawned, the entry leaves the pending
	 * queue.
	 */
	public void onChunkLoaded(org.bukkit.World world, int chunkX, int chunkZ) {
		if (pending.isEmpty()) return;
		for (java.util.Iterator<TurfPowerupData> it = pending.iterator(); it.hasNext(); ) {
			TurfPowerupData data = it.next();
			Location        loc  = data.getSpawnLocation();
			if (loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
			if (loc.getBlockX() >> 4 != chunkX || loc.getBlockZ() >> 4 != chunkZ) continue;
			it.remove();
			TurfPowerupNpc npc = TurfPowerupNpc.spawn(plugin, data, settings, civilianSpawnManager);
			if (npc != null) byTurfId.put(data.getTurfId(), npc);
		}
	}

	@Nullable
	private TurfPowerupNpc spawn(TurfPowerupData data) {
		TurfPowerupNpc existing = byTurfId.get(data.getTurfId());
		if (existing != null && existing.isAlive()) return existing;
		// Citizens NPC.spawn() silently no-ops when the destination chunk isn't loaded. On a fresh boot the
		// Quartermaster's chunk may not be loaded (no player has walked there yet) — instead of force-loading
		// and pinning the chunk in memory forever, queue the data and let TurfPowerupChunkLoadListener spawn
		// it the moment a player approaches and Bukkit naturally loads the chunk.
		Location loc = data.getSpawnLocation();
		if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
			pending.add(data);
			return null;
		}
		TurfPowerupNpc npc = TurfPowerupNpc.spawn(plugin, data, settings, civilianSpawnManager);
		if (npc == null) return null;
		byTurfId.put(data.getTurfId(), npc);
		return npc;
	}

	private void spawnAllFromRepository() {
		int spawned = 0;
		for (TurfPowerupData data : repository.loadAll()) {
			if (spawn(data) != null) spawned++;
		}
		log.debug("Spawned {} turf-powerup NPC(s) from repository", spawned);
	}

	private void despawnAll() {
		for (TurfPowerupNpc npc : byTurfId.values()) {
			try {
				npc.destroy();
			} catch (Exception exception) {
				log.warn("Failed to despawn turf-powerup NPC for turf {}: {}",
				         npc.getData().getTurfId(), exception.getMessage());
			}
		}
	}
}
