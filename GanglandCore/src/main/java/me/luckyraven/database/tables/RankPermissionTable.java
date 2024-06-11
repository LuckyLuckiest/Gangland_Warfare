package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.util.Pair;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RankPermissionTable extends Table<Pair<Rank, Permission>> {

	public RankPermissionTable(RankTable rankTable, PermissionTable permissionTable) {
		super("rank_permission");

		Attribute<Integer> rankId       = new Attribute<>("rank_id", true);
		Attribute<Integer> permissionId = new Attribute<>("permission_id", false);

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
		Map<String, Object> search = new HashMap<>();

		search.put("search", "rank_id = ? AND permission_id = ?");
		search.put("info", new Object[]{data.first().getUsedId(), data.second().getUsedId()});
		search.put("type", new int[]{Types.INTEGER, Types.INTEGER});
		search.put("index", new int[]{0, 1});

		return Collections.unmodifiableMap(search);
	}
}
