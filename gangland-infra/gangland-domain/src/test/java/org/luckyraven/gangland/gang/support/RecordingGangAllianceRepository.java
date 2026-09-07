package org.luckyraven.gangland.gang.support;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * In-memory {@link GangAllianceRepositoryContract} that records every write it is asked to perform. Written as a
 * recording fake rather than a Mockito mock per documentation/TESTING.md §6 — the contract is small and the
 * assertions read as the behaviour being pinned ("both direction rows were deleted").
 */
public final class RecordingGangAllianceRepository implements GangAllianceRepositoryContract {

	private final List<GangAlliance> stored  = new ArrayList<>();
	private final List<GangAlliance> deleted = new ArrayList<>();
	private final List<Gang>         purged  = new ArrayList<>();

	private Supplier<Collection<GangAlliance>> dataSupplier;

	/** Every alliance handed to {@link #delete(GangAlliance)}, in call order. */
	public List<GangAlliance> deleted() {
		return Collections.unmodifiableList(deleted);
	}

	/** Every gang handed to {@link #deleteAllForGang(Gang)}, in call order. */
	public List<Gang> purged() {
		return Collections.unmodifiableList(purged);
	}

	/** The rows this fake currently holds. */
	public List<GangAlliance> stored() {
		return Collections.unmodifiableList(stored);
	}

	public void seed(GangAlliance... alliances) {
		Collections.addAll(stored, alliances);
	}

	@Override
	public void setGangLookup(GangLookupContract gangLookup) {
		// no lookup resolution needed — this fake stores live GangAlliance records
	}

	@Override
	public void deleteAllForGang(Gang gang) {
		purged.add(gang);
		stored.removeIf(alliance -> alliance.gang().getId() == gang.getId()
		                            || alliance.ally().getId() == gang.getId());
	}

	@Override
	public Collection<GangAlliance> loadAll() {
		return new ArrayList<>(stored);
	}

	@Override
	public void save(GangAlliance data) {
		stored.removeIf(alliance -> alliance.gang().getId() == data.gang().getId()
		                            && alliance.ally().getId() == data.ally().getId());
		stored.add(data);
	}

	@Override
	public void saveAll(Collection<GangAlliance> collection) {
		collection.forEach(this::save);
	}

	@Override
	public void saveAllFromMemory() {
		if (dataSupplier == null) return;

		saveAll(dataSupplier.get());
	}

	@Override
	public void delete(GangAlliance data) {
		deleted.add(data);
		stored.removeIf(alliance -> alliance.gang().getId() == data.gang().getId()
		                            && alliance.ally().getId() == data.ally().getId());
	}

	@Override
	public void setDataSupplier(Supplier<Collection<GangAlliance>> dataSupplier) {
		this.dataSupplier = dataSupplier;
	}

}
