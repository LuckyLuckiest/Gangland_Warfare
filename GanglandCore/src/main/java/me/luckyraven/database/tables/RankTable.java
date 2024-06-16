package me.luckyraven.database.tables;

import me.luckyraven.data.rank.Rank;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;

import java.sql.Types;
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
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
