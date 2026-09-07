package org.luckyraven.gangland.mail.support;

import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.contract.MailRepositoryContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * In-memory recording fake for {@link MailRepositoryContract}, per TESTING.md #6 ("prefer fakes over deep mock
 * chains"). Backs {@code loadAll()} with a seed collection set once at construction (simulating what would already
 * be in the database before {@code MailManager.initialize()} runs), and separately records every {@code save} /
 * {@code delete} call so tests can assert on persistence side effects without a five-level Mockito chain.
 */
public final class FakeMailRepositoryContract implements MailRepositoryContract {

	private final Map<Long, MailItem> seed  = new LinkedHashMap<>();
	private final List<MailItem>      saved   = new ArrayList<>();
	private final List<MailItem>      deleted = new ArrayList<>();
	private Supplier<Collection<MailItem>> dataSupplier;

	public FakeMailRepositoryContract() {
	}

	public FakeMailRepositoryContract(MailItem... seedItems) {
		for (MailItem item : seedItems) seed.put(item.getId(), item);
	}

	@Override
	public Collection<MailItem> loadAll() {
		return List.copyOf(seed.values());
	}

	@Override
	public void save(MailItem data) {
		saved.add(data);
	}

	@Override
	public void saveAll(Collection<MailItem> collection) {
		saved.addAll(collection);
	}

	@Override
	public void saveAllFromMemory() {
		if (dataSupplier != null) saved.addAll(dataSupplier.get());
	}

	@Override
	public void delete(MailItem data) {
		deleted.add(data);
	}

	@Override
	public void setDataSupplier(Supplier<Collection<MailItem>> dataSupplier) {
		this.dataSupplier = dataSupplier;
	}

	public List<MailItem> saved() {
		return saved;
	}

	public List<MailItem> deleted() {
		return deleted;
	}

	public boolean hasDataSupplier() {
		return dataSupplier != null;
	}

}
