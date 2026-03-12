package me.luckyraven.database.tables.gang;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangAlliance;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class GangAllianceTable extends Table<GangAlliance> {

	public GangAllianceTable(GangTable gangTable) {
		super("gang_ally");

		Attribute<Integer> gangId = new Attribute<>("gang_id", true, Integer.class);
		Attribute<Integer> allyId = new Attribute<>("ally_id", false, Integer.class);
		Attribute<Long>    since  = new Attribute<>("since", false, Long.class);

		allyId.setUnique(true);

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
