package me.luckyraven.database.tables.rank;

import me.luckyraven.data.rank.RankParent;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankParentTable extends Table<RankParent> {

	public RankParentTable(RankTable rankTable) {
		super("rank_parent");

		Attribute<Integer> id       = new Attribute<>("id", true, Integer.class);
		Attribute<Integer> parentId = new Attribute<>("parent_id", false, Integer.class);

		parentId.setUnique(true);

		parentId.setForeignKey(rankTable.get("id"), rankTable);

		this.addAttribute(id);
		this.addAttribute(parentId);
	}

	@Override
	public Object[] getData(RankParent data) {
		return new Object[]{data.rankId(), data.parentId()};
	}

	@Override
	public Map<String, Object> searchCriteria(RankParent data) {
		return createSearchCriteria("id = ? AND parent_id = ?",
									new Object[]{data.rankId(), data.parentId()},
									new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}