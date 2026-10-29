package org.luckyraven.gangland.turf.npc;

import lombok.CustomLog;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.listener.powerups.TurfPowerupChunkLoadListener;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcPost;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
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
	private final Supplier<TurfPowerupSettings> settings;
	private final CivilianSpawnManager         civilianSpawnManager;

	/** Runs after every successful spawn (W7-C nameplate line). */
	private Consumer<TurfPowerupNpc> spawnHook = npc -> {
	};

	private static final long HOLD_PERIOD = 5L;

	private BukkitTask    holdTask;
	private IntPredicate  holding    = turfId -> false;
	private DoubleSupplier holdRadius = () -> 6.0;

	private final Map<Integer, TurfPowerupNpc>    byTurfId = new HashMap<>();
	/** T-806: turf id -> epoch millis when a KILLED Quartermaster comes back (Long.MAX_VALUE = never). */
	private final Map<Integer, Long>             respawnDue = new HashMap<>();
	/** Clock behind the respawn delay; a test swaps it. */
	LongSupplier clock = System::currentTimeMillis;
	/**
	 * Quartermaster data whose chunk wasn't loaded at spawn-attempt time. Drained by
	 * {@link #onChunkLoaded(org.bukkit.World, int, int)} as players approach and Bukkit naturally loads chunks.
	 */
	private final java.util.List<TurfPowerupData> pending  = new java.util.ArrayList<>();

	public TurfPowerupManager(JavaPlugin plugin,
	                          IRepository<TurfPowerupData> repository,
	                          Supplier<TurfPowerupSettings> settings,
	                          CivilianSpawnManager civilianSpawnManager) {
		this.plugin               = plugin;
		this.repository           = repository;
		this.settings             = settings;
		this.civilianSpawnManager = civilianSpawnManager;

		this.repository.setDataSupplier(this::snapshotData);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (holdTask == null) {
			holdTask = Bukkit.getScheduler().runTaskTimer(plugin, this::holdTick, HOLD_PERIOD, HOLD_PERIOD);
		}
		// Defer spawn so worlds + the civilian service are fully online before Citizens drops the NPCs in.
		Bukkit.getScheduler().runTaskLater(plugin, this::spawnAllFromRepository, 60L);
	}

	private void stopHoldTask() {
		if (holdTask != null) {
			holdTask.cancel();
			holdTask = null;
		}
	}

	@Override
	public void onPreClear() {
		stopHoldTask();
		despawnAll();
	}

	@Override
	public void onClear() {
		byTurfId.clear();
		respawnDue.clear();
		pending.clear();
	}

	@Override
	public void onShutdown() {
		stopHoldTask();
		despawnAll();
		byTurfId.clear();
		respawnDue.clear();
		pending.clear();
	}

	/**
	 * T-834: while {@code holding} says a turf is on the Hold order its Quartermaster still fights back (the civilian
	 * damage listener) but is walked home whenever it strays further than {@code radius} blocks from its anchor.
	 */
	public void bindHold(IntPredicate holding, DoubleSupplier radius) {
		this.holding    = holding;
		this.holdRadius = radius;
	}

	/**
	 * Every {@link #HOLD_PERIOD} ticks: walks a Quartermaster that strayed further than the Hold radius from its anchor
	 * back to its post (under Hold even mid-fight; under Defend only once it is out of combat), then brings back a
	 * Quartermaster that a chunk unload or a death took away.
	 */
	void holdTick() {
		double radius = holdRadius.getAsDouble();
		for (Map.Entry<Integer, TurfPowerupNpc> entry : new ArrayList<>(byTurfId.entrySet())) {
			TurfPowerupNpc qm = entry.getValue();
			if (!qm.isAlive()) {
				reviveIfDue(entry.getKey(), qm);
				continue;
			}
			if (qm.isRecalled()) {
				if (!holding.test(entry.getKey()) && qm.getCivilian().getCurrentState() == CivilianState.COMBAT) {
					qm.cancelRecall();
				} else {
					qm.walkHome();
				}
				continue;
			}
			CivilianNpc civilian = qm.getCivilian();
			if (!holding.test(entry.getKey()) && civilian.getCurrentState() == CivilianState.COMBAT) continue;
			Entity body = civilian.getEntity();
			Location anchor = qm.getData().getSpawnLocation();
			if (body == null || anchor.getWorld() == null || !anchor.getWorld().equals(body.getWorld())) continue;
			Location at = body.getLocation();
			double dx = at.getX() - anchor.getX();
			double dz = at.getZ() - anchor.getZ();
			if (dx * dx + dz * dz <= radius * radius) continue;
			qm.recall(new NpcPost(anchor, radius, null), holding.test(entry.getKey()));
		}
	}

	/**
	 * T-806: a dead wrapper is either a KILLED Quartermaster (back after {@code Respawn_Seconds}, never when 0) or one
	 * Citizens lost to a chunk unload (queued, spawned the moment its chunk loads - at once when it is loaded now).
	 */
	private void reviveIfDue(int turfId, TurfPowerupNpc dead) {
		Long due = respawnDue.get(turfId);
		if (due != null && clock.getAsLong() < due) return;
		respawnDue.remove(turfId);
		dead.destroy();
		byTurfId.remove(turfId);
		TurfPowerupData data = dead.getData();
		if (pending.stream().noneMatch(p -> p.getTurfId() == turfId)) pending.add(data);
		Location loc = data.getSpawnLocation();
		if (loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
			onChunkLoaded(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
		}
	}

	/** Called when this turf's Quartermaster was killed: it respawns after {@code Respawn_Seconds}, never when 0. */
	public void onKilled(int turfId) {
		int seconds = settings.get().respawnSeconds();
		respawnDue.put(turfId, seconds <= 0 ? Long.MAX_VALUE : clock.getAsLong() + seconds * 1000L);
	}

	public void setSpawnHook(Consumer<TurfPowerupNpc> spawnHook) {
		this.spawnHook = spawnHook;
	}

	private void afterSpawn(TurfPowerupNpc npc) {
		try {
			spawnHook.accept(npc);
		} catch (RuntimeException exception) {
			log.warn("Quartermaster spawn hook failed for turf {}: {}", npc.getData().getTurfId(),
			         exception.getMessage());
		}
	}

	public Collection<TurfPowerupData> snapshotData() {
		List<TurfPowerupData> snap = new ArrayList<>(byTurfId.size());
		for (TurfPowerupNpc npc : byTurfId.values()) snap.add(npc.getData());
		for (TurfPowerupData data : pending) {
			if (!byTurfId.containsKey(data.getTurfId())) snap.add(data);
		}
		return snap;
	}

	/**
	 * Place / update the powerup NPC for {@code turfId} at {@code location}. Despawns any previous NPC for the same
	 * turf, persists the new data, and spawns a fresh Quartermaster civilian. The display name passed at admin time
	 * wins; if null the configured civilian-type's display name is used.
	 */
	public TurfPowerupNpc place(int turfId, Location location, @Nullable String displayName) {
		remove(turfId);
		String name = displayName != null ? displayName : settings.get().displayName();
		TurfPowerupData data = new TurfPowerupData(turfId, location, name);
		repository.save(data);
		return spawn(data);
	}

	public void remove(int turfId) {
		respawnDue.remove(turfId);
		TurfPowerupNpc existing = byTurfId.remove(turfId);
		if (existing != null) existing.destroy();
		// TF-42: drop the queued spawn too, or a chunk load would resurrect a removed Quartermaster.
		pending.removeIf(data -> data.getTurfId() == turfId);
		// TF-33: doDelete keys on turf_id only, so no table scan is needed.
		repository.delete(new TurfPowerupData(turfId, null, null));
	}

	@Nullable
	public TurfPowerupNpc get(int turfId) {
		return byTurfId.get(turfId);
	}

	/** The Quartermaster wrapping this civilian, or null; needs no live body. */
	@Nullable
	public TurfPowerupNpc getByCivilian(CivilianNpc civilian) {
		for (TurfPowerupNpc qm : byTurfId.values()) {
			if (qm.getCivilian() == civilian) return qm;
		}
		return null;
	}

	@Nullable
	public TurfPowerupNpc getByEntity(Entity entity) {
		if (!NpcSupport.available()) return null;
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
		npc.engage(attackers, settings.get().targetingRadius());
	}

	/**
	 * The live Quartermaster civilian of {@code turfId} as a list (empty when the turf has none), for the cop guard.
	 */
	public List<CivilianNpc> civilianNpcsOf(int turfId) {
		TurfPowerupNpc npc = byTurfId.get(turfId);
		if (npc == null || !npc.isAlive()) return List.of();
		return List.of(npc.getCivilian());
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
			TurfPowerupNpc npc = TurfPowerupNpc.spawn(plugin, data, settings.get(), civilianSpawnManager);
			if (npc != null) {
				byTurfId.put(data.getTurfId(), npc);
				afterSpawn(npc);
			}
		}
	}

	@Nullable
	private TurfPowerupNpc spawn(TurfPowerupData data) {
		TurfPowerupNpc existing = byTurfId.get(data.getTurfId());
		if (existing != null && existing.isAlive()) return existing;
		// T-I6: without Citizens, CivilianSpawnManager's factory reaches CitizensAPI directly and throws. Skip the
		// spawn — the data is still saved (place()'s repository.save runs before this), so the Quartermaster comes
		// back the moment Citizens is installed and the server restarts.
		if (!NpcSupport.available()) return null;
		// Citizens NPC.spawn() silently no-ops when the destination chunk isn't loaded. On a fresh boot the
		// Quartermaster's chunk may not be loaded (no player has walked there yet) — instead of force-loading
		// and pinning the chunk in memory forever, queue the data and let TurfPowerupChunkLoadListener spawn
		// it the moment a player approaches and Bukkit naturally loads the chunk.
		Location loc = data.getSpawnLocation();
		if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
			pending.add(data);
			return null;
		}
		TurfPowerupNpc npc = TurfPowerupNpc.spawn(plugin, data, settings.get(), civilianSpawnManager);
		if (npc == null) return null;
		byTurfId.put(data.getTurfId(), npc);
		afterSpawn(npc);
		return npc;
	}

	private void spawnAllFromRepository() {
		int spawned = 0;
		for (TurfPowerupData data : repository.loadAll()) {
			if (spawn(data) != null) spawned++;
		}
		log.debug("Spawned {} turf-powerup NPC(s) from repository, {} waiting for their chunk to load", spawned,
		          pending.size());
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
