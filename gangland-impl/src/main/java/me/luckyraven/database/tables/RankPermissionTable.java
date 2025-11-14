package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.util.Pair;

import java.sql.Types;
import java.util.Map;

public class RankPermissionTable extends Table<Pair<Rank, Permission>> {

	public RankPermissionTable(RankTable rankTable, PermissionTable permissionTable) {
		super("rank_permission");

		Attribute<Integer> rankId       = new Attribute<>("rank_id", true, Integer.class);
		Attribute<Integer> permissionId = new Attribute<>("permission_id", false, Integer.class);

		permissionId.setUnique(true);

		rankId.setForeignKey(rankTable.get("id"), rankTable);
		permissionId.setForeignKey(permissionTable.get("id"), permissionTable);

		this.addAttribute(rankId);
		this.addAttribute(permissionId);
	}

	@Override
	public Object[] getData(Pair<Rank, Permission> data) {
		return new Object[]{data.first().getUsedId(), data.second().getUsedId()};
	}

	@Override
	public Map<String, Object> searchCriteria(Pair<Rank, Permission> data) {
		return createSearchCriteria("rank_id = ? AND permission_id = ?",
									new Object[]{data.first().getUsedId(), data.second().getUsedId()},
									new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}
