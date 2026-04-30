package org.luckyraven.gangland.persistence.support;

import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/**
 * Concrete {@link Table} implementation used by integration tests.
 *
 * <p>Schema: {@code test_entity (id VARCHAR(36) PK, name VARCHAR(255), score INTEGER)}
 *
 * <p>The primary key {@code id} is placed at {@code getData()} index 0.
 * {@link #searchCriteria} excludes index 0 from the UPDATE SET clause so that only {@code name} and {@code score} are
 * updated when a row already exists.
 */
public final class TestTable extends Table<TestEntity> {

	public static final String TABLE_NAME = "test_entity";

	public TestTable() {
		super(TABLE_NAME);

		Attribute<String>  id    = new Attribute<>("id", true, String.class);
		Attribute<String>  name  = new Attribute<>("name", false, String.class);
		Attribute<Integer> score = new Attribute<>("score", false, Integer.class);

		id.setCanBeNull(false);
		name.setCanBeNull(false);
		score.setCanBeNull(false);

		addAttribute(id);
		addAttribute(name);
		addAttribute(score);
	}

	@Override
	public Object[] getData(TestEntity e) {
		return new Object[]{e.getId(), e.getName(), e.getScore()};
	}

	/**
	 * WHERE clause targets the primary key {@code id}. Index 0 (the {@code id} column) is excluded from the UPDATE SET
	 * list.
	 */
	@Override
	public Map<String, Object> searchCriteria(TestEntity e) {
		return createSearchCriteria("id = ?", new Object[]{e.getId()}, new int[]{Types.VARCHAR}, new int[]{0});
	}
}
