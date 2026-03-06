package me.luckyraven.persistence.repository;

import me.luckyraven.persistence.database.component.Table;

public interface Repository<T extends Table<?>> {

	void load(T table);

	void save(T table);
}
