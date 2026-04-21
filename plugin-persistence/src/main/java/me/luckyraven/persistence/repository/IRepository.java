package me.luckyraven.persistence.repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.function.Supplier;

public interface IRepository<T> {

	Collection<T> loadAll();

	void save(T data);

	void saveAll(Collection<T> collection);

	void saveAllFromMemory();

	void delete(T data);

	void setDataSupplier(Supplier<Collection<T>> dataSupplier);

	/**
	 * One-shot schema migration run once per startup after the table has been created/validated. Implementations
	 * override to perform idempotent structural fixes that the table attribute model cannot express. Default is no-op.
	 */
	default void migrateSchema() throws SQLException {
	}
}
