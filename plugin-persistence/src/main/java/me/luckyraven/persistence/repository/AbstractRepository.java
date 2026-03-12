package me.luckyraven.persistence.repository;

import lombok.Getter;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
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
		AtomicReference<Collection<T>> collection = new AtomicReference<>();

		databaseHelper.runQueriesAsync(database -> collection.set(doLoadAll()));

		return collection.get();
	}

	@Override
	public void save(T data) {
		databaseHelper.runQueriesAsync(database -> saveRow(data, getDatabase()));
	}

	@Override
	public void saveAll(Collection<T> collection) {
		databaseHelper.runQueriesAsync(database -> {
			for (T row : collection) {
				saveRow(row, database);
			}
		});
	}

	@Override
	public void saveAllFromMemory() {
		if (dataSupplier == null) {
			throw new IllegalStateException("No data supplier set for repository: " + getClass().getSimpleName());
		}
		saveAll(dataSupplier.get());
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

	protected boolean isRowAvailable(T data, Database database) throws SQLException {
		Table<T> table   = getTable();
		Database dbTable = database.table(table.getName());

		Map<String, Object> search = table.searchCriteria(data);
		Object[] select = dbTable.select((String) search.get("search"), (Object[]) search.get("info"),
										 (int[]) search.get("types"), new String[]{"*"});
		return select.length != 0;
	}

	protected void insertData(T data, Database database) throws SQLException {
		getTable().insertTableQuery(database, data);
	}

	protected void updateData(T data, Database database) throws SQLException {
		getTable().updateTableQuery(database, data);
	}

	private void saveRow(T data, Database database) throws SQLException {
		consumeSave(data);

		boolean checkRow = isRowAvailable(data, database);

		if (checkRow) {
			updateData(data, database);
		} else {
			insertData(data, database);
		}
	}

	private <E> void consumeSave(E data) {
		Consumer<E> consumer = processSave();
		if (consumer != null) consumer.accept(data);
	}
}