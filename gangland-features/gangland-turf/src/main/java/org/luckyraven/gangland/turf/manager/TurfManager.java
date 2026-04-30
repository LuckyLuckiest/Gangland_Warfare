package org.luckyraven.gangland.turf.manager;

import lombok.CustomLog;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for turfs. Holds the persisted definitions plus per-turf in-memory runtime state (capture progress
 * etc.). Persistence is delegated to the {@link TurfRepositoryContract} — the repo lives in gangland-impl.
 *
 * <p>Turf ids are auto-incrementing integers; callers ask for a fresh id via {@link #allocateId()} before constructing
 * a new {@link Turf}. On {@link #initialize()} the counter is seeded to {@code max(existingIds) + 1} so restart-safe.
 */
@CustomLog
public final class TurfManager {

	private final TurfRepositoryContract         repository;
	private final Map<Integer, Turf>             turfsById;
	private final Map<String, List<Turf>>        turfsByWorld;
	private final Map<Integer, TurfRuntimeState> runtimeStates;
	private final AtomicInteger                  nextId = new AtomicInteger(1);

	public TurfManager(TurfRepositoryContract repository) {
		this.repository    = repository;
		this.turfsById     = new ConcurrentHashMap<>();
		this.turfsByWorld  = new ConcurrentHashMap<>();
		this.runtimeStates = new ConcurrentHashMap<>();
	}

	/**
	 * Called after the repository has loaded persisted turfs. Re-populates the by-world index, creates a fresh IDLE
	 * runtime state per turf (in-flight captures do not survive restart per spec), and seeds the id allocator to one
	 * past the highest existing id.
	 */
	public void initialize() {
		turfsById.clear();
		turfsByWorld.clear();
		runtimeStates.clear();

		Collection<Turf> loaded  = repository.loadAll();
		int              highest = 0;
		for (Turf turf : loaded) {
			register(turf);
			if (turf.getId() > highest) {
				highest = turf.getId();
			}
		}
		nextId.set(highest + 1);

		// Hand the repo a supplier so RepositoryRegistry.saveAll() (periodic autosave + shutdown)
		// can flush the current in-memory turf set to the DB. Without this, saveAllFromMemory()
		// throws IllegalStateException("No data supplier set for repository: TurfRepository").
		repository.setDataSupplier(turfsById::values);

		log.debug("Loaded {} turf(s) across {} world(s); next id = {}",
		          turfsById.size(), turfsByWorld.size(), nextId.get());
	}

	public int allocateId() {
		return nextId.getAndIncrement();
	}

	public Collection<Turf> getAll() {
		return Collections.unmodifiableCollection(turfsById.values());
	}

	public @Nullable Turf get(int id) {
		return turfsById.get(id);
	}

	public List<Turf> getTurfsInWorld(String world) {
		return turfsByWorld.getOrDefault(world, Collections.emptyList());
	}

	public TurfRuntimeState getRuntimeState(int turfId) {
		return runtimeStates.get(turfId);
	}

	public @Nullable Turf findAt(Location location) {
		if (location == null || location.getWorld() == null) {
			return null;
		}
		// Overlap is rejected at creation so the first match is unambiguous.
		for (Turf turf : getTurfsInWorld(location.getWorld().getName())) {
			if (turf.getRegion().contains(location)) {
				return turf;
			}
		}
		return null;
	}

	/**
	 * @return {@code null} if {@code newRegion} does not overlap any existing turf in its world, otherwise the existing
	 * 		turf that conflicts.
	 */
	public @Nullable Turf findConflict(CuboidRegion newRegion) {
		for (Turf existing : getTurfsInWorld(newRegion.getWorld())) {
			if (existing.getRegion().overlaps(newRegion)) {
				return existing;
			}
		}
		return null;
	}

	public void create(Turf turf) {
		register(turf);
		repository.save(turf);
	}

	public void delete(Turf turf) {
		turfsById.remove(turf.getId());
		List<Turf> worldTurfs = turfsByWorld.get(turf.getRegion().getWorld());
		if (worldTurfs != null) {
			worldTurfs.remove(turf);
		}
		runtimeStates.remove(turf.getId());
		repository.delete(turf);
	}

	/**
	 * Called whenever ownership or the persisted capture timestamp changes. Bypasses the autosave cadence — capture is
	 * an important enough state change that it should hit disk immediately.
	 */
	public void persist(Turf turf) {
		repository.save(turf);
	}

	private void register(Turf turf) {
		turfsById.put(turf.getId(), turf);
		turfsByWorld.computeIfAbsent(turf.getRegion().getWorld(), k -> new ArrayList<>()).add(turf);
		runtimeStates.put(turf.getId(), new TurfRuntimeState(turf.getId()));
	}
}
