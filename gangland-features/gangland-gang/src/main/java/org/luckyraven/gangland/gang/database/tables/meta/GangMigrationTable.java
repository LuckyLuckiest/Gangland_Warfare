package org.luckyraven.gangland.gang.database.tables.meta;

import org.luckyraven.gangland.gang.database.repositories.meta.GangMigration;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** Schema for {@code gang_migration}: one row per one-shot gang data migration that ran (W2-K). */
public class GangMigrationTable extends Table<GangMigration> {

	public GangMigrationTable() {
		super("gang_migration");

		Attribute<String> id     = new Attribute<>("id", true, String.class);
		Attribute<Long>   doneAt = new Attribute<>("done_at", false, Long.class);

		this.addAttribute(id);
		this.addAttribute(doneAt);
	}

	@Override
	public Object[] getData(GangMigration data) {
		return new Object[]{data.id(), data.doneAt()};
	}

	@Override
	public Map<String, Object> searchCriteria(GangMigration data) {
		return createSearchCriteria("id = ?", new Object[]{data.id()}, new int[]{Types.VARCHAR}, new int[]{0});
	}

}
