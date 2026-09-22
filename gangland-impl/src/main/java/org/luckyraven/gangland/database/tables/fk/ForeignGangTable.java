package org.luckyraven.gangland.database.tables.fk;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.util.Map;

/**
 * Foreign-key reference stand-in for the gang module's real {@code GangTable} (table {@code gang}, primary key
 * {@code id}, type {@link Integer}). Mirrors the gang module's own {@code ForeignUserTable}/{@code
 * ForeignPermissionTable} in reverse: gangland-impl cannot import the module's persistence classes (impl → module
 * is forbidden), but {@code WaypointTable.gang_id} needs a real {@code Table<?>}/{@code Attribute<?>} pair to
 * declare its foreign key against. Reproduces {@code GangTable}'s name and {@code id} column exactly so the
 * backend's FK DDL is identical to what the real table produces — table names and schema unchanged, no migration
 * (WS5 G1-G3, mirrors G1 step 7/C7). Never used for actual persistence: {@link #getData}/{@link #searchCriteria}
 * are unreachable in practice — only {@code WaypointTable}'s constructor reads {@link #get(String)} off it.
 */
public final class ForeignGangTable extends Table<Object> {

	public ForeignGangTable() {
		super("gang");

		addAttribute(new Attribute<>("id", true, Integer.class));
	}

	@Override
	public Object[] getData(Object data) {
		throw new UnsupportedOperationException("ForeignGangTable is a foreign-key reference only");
	}

	@Override
	public Map<String, Object> searchCriteria(Object data) {
		throw new UnsupportedOperationException("ForeignGangTable is a foreign-key reference only");
	}
}
