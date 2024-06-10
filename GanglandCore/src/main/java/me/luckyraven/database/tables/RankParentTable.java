package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RankParentTable extends Table<Rank> {

	public RankParentTable(RankTable rankTable) {
		super("rank_parent");

		Attribute<Integer> id       = new Attribute<>("id", true);
		Attribute<Integer> parentId = new Attribute<>("parent_id", false);

		parentId.setUnique(true);

		parentId.setForeignKey(rankTable.get("id"), rankTable);

		this.addAttribute(id);
		this.addAttribute(parentId);
	}

	@Override
	public Object[] getData(Rank data) {
		return new Object[]{data.getUsedId()};
	}

	@Override
	public Map<String, Object> searchCriteria(Rank data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "id = ?");
		search.put("info", new Object[]{data.getUsedId()});
		search.put("type", new int[]{Types.INTEGER});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
