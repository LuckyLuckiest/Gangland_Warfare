package me.luckyraven.persistence.repository;

import java.util.Collection;
import java.util.function.Supplier;

public interface IRepository<T> {

	Collection<T> loadAll();

	void save(T data);

	void saveAll(Collection<T> collection);

	void saveAllFromMemory();

	void delete(T data);

	void setDataSupplier(Supplier<Collection<T>> dataSupplier);
}
