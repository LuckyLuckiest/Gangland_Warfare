package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.turf.powerups.ActiveBuffRepositoryContract;
import org.luckyraven.gangland.turf.powerups.ActiveTurfBuff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * In-memory stand-in for the real {@code ActiveTurfBuffRepository} (gangland-impl), backing {@link
 * org.luckyraven.gangland.turf.powerups.ActiveBuffManager} in tests without SQLite.
 */
public final class InMemoryActiveBuffRepository implements ActiveBuffRepositoryContract {

	private final Map<Long, ActiveTurfBuff> rows    = new LinkedHashMap<>();
	public  final List<ActiveTurfBuff>      deleted = new ArrayList<>();

	public void seed(ActiveTurfBuff buff) {
		rows.put(buff.getId(), buff);
	}

	@Override
	public Collection<ActiveTurfBuff> loadAll() {
		return new ArrayList<>(rows.values());
	}

	@Override
	public void save(ActiveTurfBuff data) {
		rows.put(data.getId(), data);
	}

	@Override
	public void saveAll(Collection<ActiveTurfBuff> collection) {
		for (ActiveTurfBuff buff : collection) {
			save(buff);
		}
	}

	@Override
	public void saveAllFromMemory() {
		// unused
	}

	@Override
	public void delete(ActiveTurfBuff data) {
		rows.remove(data.getId());
		deleted.add(data);
	}

	@Override
	public void setDataSupplier(Supplier<Collection<ActiveTurfBuff>> dataSupplier) {
		// unused — tests drive save/delete directly
	}
}
