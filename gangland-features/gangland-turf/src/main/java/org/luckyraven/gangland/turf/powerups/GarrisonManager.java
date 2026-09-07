package org.luckyraven.gangland.turf.powerups;

import lombok.CustomLog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache of per-turf garrison stock with the repository as the persistence backing. All reads and writes go
 * through this manager; the repository is treated as a write-through pass-through. {@link #consume(int, int)} returns
 * the number actually consumed (may be less than requested if stock is low), so callers don't need to pre-check.
 */
@CustomLog
public final class GarrisonManager {

	private final GarrisonRepositoryContract repository;
	private final Map<Integer, Garrison>     byTurf = new HashMap<>();

	public GarrisonManager(GarrisonRepositoryContract repository) {
		this.repository = repository;
	}

	public void initialize() {
		byTurf.clear();
		for (Garrison g : repository.loadAll()) {
			byTurf.put(g.getTurfId(), g);
		}
		repository.setDataSupplier(this::snapshot);
		log.debug("Loaded {} turf garrison row(s)", byTurf.size());
	}

	public int count(int turfId) {
		Garrison g = byTurf.get(turfId);
		return g == null ? 0 : g.getCount();
	}

	public void add(int turfId, int delta) {
		if (delta <= 0) return;
		Garrison g = byTurf.get(turfId);
		if (g == null) {
			g = new Garrison(turfId, delta);
			byTurf.put(turfId, g);
		} else {
			g.setCount(g.getCount() + delta);
		}
		repository.save(g);
	}

	/**
	 * Consumes up to {@code requested} defenders from the turf's stock. Returns the actual number consumed (capped at
	 * the available count, never negative). The row is left at zero rather than deleted so the panel can still show the
	 * turf has a garrison slot — re-buy keeps the same row.
	 */
	public int consume(int turfId, int requested) {
		if (requested <= 0) return 0;
		Garrison g = byTurf.get(turfId);
		if (g == null || g.getCount() <= 0) return 0;
		int actual = Math.min(requested, g.getCount());
		g.setCount(g.getCount() - actual);
		repository.save(g);
		return actual;
	}

	private Collection<Garrison> snapshot() {
		return byTurf.values();
	}
}
