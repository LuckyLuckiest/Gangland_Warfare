package org.luckyraven.gangland.database.tables.gang;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class GangAllianceTable extends Table<GangAlliance> {

	public GangAllianceTable(GangTable gangTable) {
		super("gang_ally");

		// Composite primary key: an alliance is identified by the (gang_id, ally_id) pair, and each direction is
		// stored as its own row. Keying on gang_id alone made the upsert conflict target a single column, so a gang
		// with two or more allies collapsed to one row on every save (GR-05). ally_id must NOT be UNIQUE either —
		// a gang may appear as the allied side of several alliances.
		Attribute<Integer> gangId = new Attribute<>("gang_id", true, Integer.class);
		Attribute<Integer> allyId = new Attribute<>("ally_id", true, Integer.class);
		Attribute<Long>    since  = new Attribute<>("since", false, Long.class);

		since.setDefaultValue(-1L);

		gangId.setForeignKey(gangTable.get("id"), gangTable);
		allyId.setForeignKey(gangTable.get("id"), gangTable);

		this.addAttribute(gangId);
		this.addAttribute(allyId);
		this.addAttribute(since);
	}

	@Override
	public Object[] getData(GangAlliance data) {
		Gang originalGang = data.gang();
		Gang alliedGang   = data.ally();

		return new Object[]{originalGang.getId(), alliedGang.getId(), data.since()};
	}

	@Override
	public Map<String, Object> searchCriteria(GangAlliance data) {
		return createSearchCriteria("gang_id = ? AND ally_id = ?",
		                            new Object[]{data.gang().getId(), data.ally().getId()},
		                            new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}
