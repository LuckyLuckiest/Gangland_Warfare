package org.luckyraven.gangland.gang.database.tables.rank;

import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class RankTable extends Table<Rank> {

	public RankTable() {
		super("rank_tree");

		Attribute<Integer> id         = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name       = new Attribute<>("name", false, String.class);
		Attribute<String>  vaultGroup = new Attribute<>("vault_group", false, String.class);

		// The domain treats an unlinked rank as vault_group = null. Databases created before this flag keep a
		// legacy NOT NULL DEFAULT '' column (applySchema never relaxes a constraint), so getData stores an
		// unlinked rank as '' and RankRepository.doLoadAll maps '' back to null.
		vaultGroup.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(name);
		this.addAttribute(vaultGroup);
	}

	@Override
	public Object[] getData(Rank data) {
		String vaultGroup = data.getVaultGroup();
		return new Object[]{data.getUsedId(), data.getName(), vaultGroup == null ? "" : vaultGroup};
	}

	@Override
	public Map<String, Object> searchCriteria(Rank data) {
		return createSearchCriteria("id = ?", new Object[]{data.getUsedId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
