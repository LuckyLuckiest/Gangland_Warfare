package me.luckyraven.database.tables;

import me.luckyraven.Pair;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankParentTable extends Table<Pair<Rank, Rank>> {

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
	public Object[] getData(Pair<Rank, Rank> data) {
		return new Object[]{data.first().getUsedId(), data.second().getUsedId()};
	}

	@Override
	public Map<String, Object> searchCriteria(Pair<Rank, Rank> data) {
		return createSearchCriteria("id = ? AND parent_id = ?",
									new Object[]{data.first().getUsedId(), data.second().getUsedId()},
									new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}
