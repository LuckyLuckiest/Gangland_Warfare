package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RankTable extends Table<Rank> {

	public RankTable() {
		super("rank_tree");

		Attribute<Integer> id   = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name = new Attribute<>("name", false, String.class);

		this.addAttribute(id);
		this.addAttribute(name);
	}

	@Override
	public Object[] getData(Rank data) {
		return new Object[]{data.getUsedId(), data.getName()};
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
