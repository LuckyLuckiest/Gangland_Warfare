package org.luckyraven.gangland.persistence.repository;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseHelper;
import org.luckyraven.gangland.persistence.database.component.Table;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractRepository<T> implements IRepository<T> {

	@Getter
	private final JavaPlugin     plugin;
	private final DatabaseHelper databaseHelper;

	private Supplier<Collection<T>> dataSupplier;

	public AbstractRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		this.plugin         = plugin;
		this.databaseHelper = new DatabaseHelper(plugin, databaseHandler);
	}

	/**
	 * Loads data from database - called during initialization
	 */
	protected abstract Collection<T> doLoadAll() throws SQLException;

	/**
	 * Optional pre-save processing
	 */
	protected abstract <E> Consumer<E> processSave();

	/**
	 * Provides the table definition for this repository
	 */
	protected abstract Table<T> getTable();

	/**
	 * Deletes data from the database
	 */
	protected abstract void doDelete(T data) throws SQLException;

	@Override
	public void setDataSupplier(Supplier<Collection<T>> dataSupplier) {
		this.dataSupplier = dataSupplier;
	}

	@Override
	public Collection<T> loadAll() {
		AtomicReference<Collection<T>> collection = new AtomicReference<>(Collections.emptyList());

		databaseHelper.runQueries(database -> collection.set(doLoadAll()));

		return collection.get();
	}

	@Override
	public void save(T data) {
		databaseHelper.runQueriesAsync(database -> {
			consumeSave(data);
			getTable().upsertTableQuery(database, data);
		});
	}

	@Override
	public void saveAll(Collection<T> collection) {
		saveAll(collection, null);
	}

	public void saveAll(Collection<T> collection, Runnable onComplete) {
		List<T> snapshot = new ArrayList<>(collection);
		databaseHelper.runQueriesAsync(database -> {
			for (T row : snapshot) {
				consumeSave(row);
			}
			getTable().batchUpsertTableQuery(database, snapshot);
		}, onComplete);
	}

	@Override
	public void saveAllFromMemory() {
		saveAllFromMemory(null);
	}

	public void saveAllFromMemory(Runnable onComplete) {
		if (dataSupplier == null) {
			throw new IllegalStateException("No data supplier set for repository: " + getClass().getSimpleName());
		}
		saveAll(dataSupplier.get(), onComplete);
	}

	@Override
	public void delete(T data) {
		databaseHelper.runQueriesAsync(database -> doDelete(data));
	}

	public DatabaseHandler getDatabaseHandler() {
		return databaseHelper.getDatabaseHandler();
	}

	public Database getDatabase() {
		return getDatabaseHandler().getDatabase();
	}

	private <E> void consumeSave(E data) {
		Consumer<E> consumer = processSave();
		if (consumer != null) consumer.accept(data);
	}
}