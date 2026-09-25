package org.luckyraven.gangland.gang.database.tables.rank;

import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankParentTable extends Table<RankParent> {

	public RankParentTable(RankTable rankTable) {
		super("rank_parent");

		Attribute<Integer> id       = new Attribute<>("id", true, Integer.class);
		// Composite key: a rank may hold several links (a branching hierarchy). A sole key on id made each save of a
		// second link overwrite the first; RankParentRepository#migrateSchema flips legacy tables.
		Attribute<Integer> parentId = new Attribute<>("parent_id", true, Integer.class);

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