package org.luckyraven.gangland.database.tables.rank;

import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankTable extends Table<Rank> {

	public RankTable() {
		super("rank_tree");

		Attribute<Integer> id         = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name       = new Attribute<>("name", false, String.class);
		Attribute<String>  vaultGroup = new Attribute<>("vault_group", false, String.class);

		this.addAttribute(id);
		this.addAttribute(name);
		this.addAttribute(vaultGroup);
	}

	@Override
	public Object[] getData(Rank data) {
		return new Object[]{data.getUsedId(), data.getName(), data.getVaultGroup()};
	}

	@Override
	public Map<String, Object> searchCriteria(Rank data) {
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
