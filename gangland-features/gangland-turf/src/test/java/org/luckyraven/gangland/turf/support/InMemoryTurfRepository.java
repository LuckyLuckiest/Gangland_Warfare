package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.Turf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * In-memory stand-in for the real {@code TurfRepository} (gangland-impl). Backs {@link
 * org.luckyraven.gangland.turf.manager.TurfManager} in tests without touching SQLite — the manager's own
 * indexing logic is what's under test, not persistence.
 */
public final class InMemoryTurfRepository implements TurfRepositoryContract {

	private final Map<Integer, Turf> rows = new LinkedHashMap<>();
	public  final List<Turf>         deleted = new ArrayList<>();

	public void seed(Turf turf) {
		rows.put(turf.getId(), turf);
	}

	@Override
	public Collection<Turf> loadAll() {
		return new ArrayList<>(rows.values());
	}

	@Override
	public void save(Turf data) {
		rows.put(data.getId(), data);
	}

	@Override
	public void saveAll(Collection<Turf> collection) {
		for (Turf turf : collection) {
			save(turf);
		}
	}

	@Override
	public void saveAllFromMemory() {
		// no data supplier wiring needed for these tests
	}

	@Override
	public void delete(Turf data) {
		rows.remove(data.getId());
		deleted.add(data);
	}

	@Override
	public void setDataSupplier(Supplier<Collection<Turf>> dataSupplier) {
		// unused — tests drive save/delete directly
	}
}
