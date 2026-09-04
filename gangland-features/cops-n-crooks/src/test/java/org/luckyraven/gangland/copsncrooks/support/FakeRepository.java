package org.luckyraven.gangland.copsncrooks.support;

import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Minimal in-memory {@link IRepository} test double. Records every {@link #save}/{@link #delete} call (in order) so
 * a test can assert what a service wrote, and {@link #loadAll()} simply replays whatever was last saved — good
 * enough for the cops-n-crooks services under test, none of which need real SQL semantics (upsert-by-key, etc).
 *
 * <p>Per {@code documentation/TESTING.md} §6 ("prefer fakes over deep mock chains"), this stands in for
 * {@code mock(IRepository.class)} wherever a test wants to assert persistence calls happened without asserting on a
 * five-level Mockito {@code verify} chain.
 */
public final class FakeRepository<T> implements IRepository<T> {

	public final List<T> saved   = new ArrayList<>();
	public final List<T> deleted = new ArrayList<>();

	private Supplier<Collection<T>> dataSupplier;

	@Override
	public Collection<T> loadAll() {
		return new ArrayList<>(saved);
	}

	@Override
	public void save(T data) {
		saved.add(data);
	}

	@Override
	public void saveAll(Collection<T> collection) {
		saved.addAll(collection);
	}

	@Override
	public void saveAllFromMemory() {
		if (dataSupplier == null) return;
		saved.addAll(dataSupplier.get());
	}

	@Override
	public void delete(T data) {
		deleted.add(data);
		saved.remove(data);
	}

	@Override
	public void setDataSupplier(Supplier<Collection<T>> dataSupplier) {
		this.dataSupplier = dataSupplier;
	}

	/** Seeds {@link #loadAll()} without going through {@link #save}, so {@link #saved}/{@link #deleted} stay clean. */
	public void seed(Collection<T> rows) {
		saved.addAll(rows);
	}

}
