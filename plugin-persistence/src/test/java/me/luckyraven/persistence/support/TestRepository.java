package me.luckyraven.persistence.support;

import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Concrete {@link AbstractRepository} for {@link TestEntity}, used in integration tests.
 *
 * <p>Exposes {@link #getTablePublic()} so tests can access the table definition without
 * reflection (the base-class {@code getTable()} is {@code protected}).
 */
public final class TestRepository extends AbstractRepository<TestEntity> {

	private final TestTable table = new TestTable();

	public TestRepository(JavaPlugin plugin, DatabaseHandler handler) {
		super(plugin, handler);
	}

	/**
	 * Visible accessor so tests can read the table definition without reflection.
	 */
	public TestTable getTablePublic() { return table; }

	@Override
	protected Collection<TestEntity> doLoadAll() throws SQLException {
		List<TestEntity> entities = new ArrayList<>();
		List<Object[]>   rows     = getDatabase().table(table.getName()).selectAll();

		for (Object[] row : rows) {
			String id    = (String) row[0];
			String name  = (String) row[1];
			int    score = ((Number) row[2]).intValue();
			entities.add(new TestEntity(id, name, score));
		}

		return entities;
	}

	/**
	 * No pre-save processing required for test entities.
	 */
	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<TestEntity> getTable() {
		return table;
	}

	@Override
	protected void doDelete(TestEntity data) throws SQLException {
		getDatabase().table(table.getName()).delete("id", data.getId(), Types.VARCHAR);
	}
}
