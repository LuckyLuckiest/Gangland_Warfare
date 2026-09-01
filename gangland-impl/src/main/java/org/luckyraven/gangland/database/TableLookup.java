package org.luckyraven.gangland.database;

import org.luckyraven.keystone.persistence.database.component.Table;

import java.util.List;

/**
 * Stateless helper for picking a concrete {@link Table} subtype out of a {@link GanglandDatabase}'s table list.
 *
 * <p>Lives in the database package so any caller that already has a {@link GanglandDatabase} reference can perform the
 * lookup without additional dependencies.
 */
public final class TableLookup {

	private TableLookup() {
	}

	/**
	 * Returns the first {@link Table} in {@code tables} that is assignable to {@code type}.
	 *
	 * @throws IllegalStateException if no matching table is registered
	 */
	public static <E extends Table<?>> E find(Class<E> type, List<Table<?>> tables) {
		return tables.stream()
				.filter(type::isInstance)
				.map(type::cast)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No table of type " + type.getName() + " is registered in GanglandDatabase"));
	}
}
