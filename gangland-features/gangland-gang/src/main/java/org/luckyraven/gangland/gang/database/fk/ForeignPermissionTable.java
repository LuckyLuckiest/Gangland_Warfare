package org.luckyraven.gangland.gang.database.fk;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.util.Map;

/**
 * Foreign-key reference stand-in for gangland-impl's real {@code PermissionTable} (table {@code permission},
 * primary key {@code id}). See {@link ForeignUserTable} for the full rationale — the module can't import
 * gangland-impl's persistence classes directly, so {@code RankPermissionTable.permissionId} needs this
 * name-and-column-exact reference instead of the real {@code PermissionTable} to declare its foreign key.
 * Never used for actual persistence.
 */
public final class ForeignPermissionTable extends Table<Object> {

	public ForeignPermissionTable() {
		super("permission");

		addAttribute(new Attribute<>("id", true, Integer.class));
	}

	@Override
	public Object[] getData(Object data) {
		throw new UnsupportedOperationException("ForeignPermissionTable is a foreign-key reference only");
	}

	@Override
	public Map<String, Object> searchCriteria(Object data) {
		throw new UnsupportedOperationException("ForeignPermissionTable is a foreign-key reference only");
	}
}
