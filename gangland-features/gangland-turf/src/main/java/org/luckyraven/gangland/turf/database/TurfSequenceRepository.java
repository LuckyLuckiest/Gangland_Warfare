package org.luckyraven.gangland.turf.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.contract.TurfSequenceContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Persists the turf id high-water mark (TF-05). The in-memory value only ever grows, and autosave/shutdown write it
 * through the data supplier like every other repository.
 */
@Repository(TurfSequence.class)
public class TurfSequenceRepository extends AbstractRepository<TurfSequence> implements TurfSequenceContract {

	private final TurfSequenceTable table;
	private volatile int            highest;

	public TurfSequenceRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.table = new TurfSequenceTable();
		// Nothing to write until loadHighWaterMark() ran: an unloaded repository must not overwrite the stored mark with 0.
		setDataSupplier(() -> highest == 0 ? List.of() : List.of(new TurfSequence(1, highest)));
	}

	@Override
	public int loadHighWaterMark() {
		int stored = 0;
		for (TurfSequence row : loadAll()) {
			stored = Math.max(stored, row.highestId());
		}
		highest = stored;
		return stored;
	}

	@Override
	public void saveHighWaterMark(int highestAllocatedId) {
		highest = Math.max(highest, highestAllocatedId);
		// ponytail: save() is async, so two allocations in one tick can land out of order; the next autosave or
		// shutdown rewrites the max through the data supplier. Add per-write sequencing if that window matters.
		save(new TurfSequence(1, highest));
	}

	@Override
	protected Collection<TurfSequence> doLoadAll() throws SQLException {
		List<TurfSequence> rows = new ArrayList<>();
		for (Object[] result : tableBackend().selectAll()) {
			rows.add(new TurfSequence(((Number) result[0]).intValue(), ((Number) result[1]).intValue()));
		}
		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<TurfSequence> getTable() {
		return table;
	}

	@Override
	protected void doDelete(TurfSequence data) throws SQLException {
		tableBackend().delete("id = ?", data.id());
	}
}
