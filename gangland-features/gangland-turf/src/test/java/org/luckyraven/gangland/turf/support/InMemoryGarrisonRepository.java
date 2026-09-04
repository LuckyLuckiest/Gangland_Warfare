package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.turf.powerups.Garrison;
import org.luckyraven.gangland.turf.powerups.GarrisonRepositoryContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * In-memory stand-in for the real {@code TurfGarrisonRepository} (gangland-impl), backing {@link
 * org.luckyraven.gangland.turf.powerups.GarrisonManager} in tests without SQLite.
 */
public final class InMemoryGarrisonRepository implements GarrisonRepositoryContract {

	private final Map<Integer, Garrison> rows  = new LinkedHashMap<>();
	public  final List<Garrison>         saved = new ArrayList<>();

	public void seed(Garrison garrison) {
		rows.put(garrison.getTurfId(), garrison);
	}

	@Override
	public Collection<Garrison> loadAll() {
		return new ArrayList<>(rows.values());
	}

	@Override
	public void save(Garrison data) {
		rows.put(data.getTurfId(), data);
		saved.add(data);
	}

	@Override
	public void saveAll(Collection<Garrison> collection) {
		for (Garrison garrison : collection) {
			save(garrison);
		}
	}

	@Override
	public void saveAllFromMemory() {
		// unused
	}

	@Override
	public void delete(Garrison data) {
		rows.remove(data.getTurfId());
	}

	@Override
	public void setDataSupplier(Supplier<Collection<Garrison>> dataSupplier) {
		// unused — tests drive save directly
	}
}
