package org.luckyraven.gangland.gang.database.repositories.meta;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.database.tables.meta.GangMigrationTable;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Records which one-shot gang data migrations ran ({@code gang_migration}, W2-K). A migration is looked up once from
 * the table and then answered from memory; {@link #markDone} writes through the normal repository save.
 */
@Repository(GangMigration.class)
public class GangMigrationRepository extends AbstractRepository<GangMigration> implements GangMigrationsContract {

	private final GangMigrationTable table;
	private final Set<String>        done = new HashSet<>();
	private       boolean            loaded;

	public GangMigrationRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);
		this.table = new GangMigrationTable();
		// Nothing to write until the table was read: an unloaded repository must not erase the stored migrations.
		setDataSupplier(this::rows);
	}

	/**
	 * Saves {@code rows}, then runs {@code then} once the write finished (AbstractRepository's callback), so a migration
	 * is marked done only after its data is written. A repository without the callback saves and runs {@code then} at
	 * once.
	 */
	public static <T> void saveThen(IRepository<T> repository, Collection<T> rows, Runnable then) {
		if (repository instanceof AbstractRepository<T> abstractRepository) {
			abstractRepository.saveAll(rows, then);
			return;
		}
		repository.saveAll(rows);
		then.run();
	}

	@Override
	public synchronized boolean isDone(String id) {
		if (!loaded) {
			for (GangMigration row : loadAll()) {
				done.add(row.id());
			}
			loaded = true;
		}
		return done.contains(id);
	}

	@Override
	public synchronized void markDone(String id) {
		done.add(id);
		save(new GangMigration(id, System.currentTimeMillis()));
	}

	private synchronized List<GangMigration> rows() {
		List<GangMigration> rows = new ArrayList<>();
		for (String id : done) {
			rows.add(new GangMigration(id, System.currentTimeMillis()));
		}
		return rows;
	}

	@Override
	protected Collection<GangMigration> doLoadAll() throws SQLException {
		List<GangMigration> rows = new ArrayList<>();
		for (Object[] result : tableBackend().selectAll()) {
			long doneAt = result[1] instanceof Number number ? number.longValue() : 0L;
			rows.add(new GangMigration(String.valueOf(result[0]), doneAt));
		}
		return rows;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<GangMigration> getTable() {
		return table;
	}

	@Override
	protected void doDelete(GangMigration data) throws SQLException {
		tableBackend().delete("id = ?", data.id());
	}

}
