package me.luckyraven.database.tables.rank;

import me.luckyraven.data.rank.RankPermission;
import me.luckyraven.database.tables.plugin.PermissionTable;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankPermissionTable extends Table<RankPermission> {

	public RankPermissionTable(RankTable rankTable, PermissionTable permissionTable) {
		super("rank_permission");

		// Junction table — composite primary key (rank_id, permission_id). Marking rank_id as the sole PK collapses
		// every rank's perms into one row on upsert; marking permission_id UNIQUE blocks sharing a permission across
		// ranks. Both are wrong for a junction. Legacy databases created before this fix are migrated in-place by
		// RankPermissionRepository#migrateSchemaIfNeeded.
		Attribute<Integer> rankId       = new Attribute<>("rank_id", true, Integer.class);
		Attribute<Integer> permissionId = new Attribute<>("permission_id", true, Integer.class);

		rankId.setForeignKey(rankTable.get("id"), rankTable);
		permissionId.setForeignKey(permissionTable.get("id"), permissionTable);

		this.addAttribute(rankId);
		this.addAttribute(permissionId);
	}

	@Override
	public Object[] getData(RankPermission data) {
		return new Object[]{data.rankId(), data.permissionId()};
	}

	@Override
	public Map<String, Object> searchCriteria(RankPermission data) {
		return createSearchCriteria("rank_id = ? AND permission_id = ?",
									new Object[]{data.rankId(), data.permissionId()},
									new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}
