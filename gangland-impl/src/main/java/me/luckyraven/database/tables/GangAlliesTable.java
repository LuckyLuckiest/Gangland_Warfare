package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.util.Pair;

import java.sql.Types;
import java.util.Map;

public class GangAlliesTable extends Table<Pair<Gang, Gang>> {

	public GangAlliesTable(GangTable gangTable) {
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
	public Object[] getData(Pair<Gang, Gang> data) {
		Gang originalGang = data.first();
		Gang alliedGang   = data.second();

		// get the allied gang date
		long alliedDate = originalGang.getAllies()
				.stream()
				.filter(pair -> pair.first().equals(alliedGang))
				.map(Pair::second)
				.mapToLong(Long::longValue)
				.toArray()[0];

		return new Object[]{data.first().getId(), data.second().getId(), alliedDate};
	}

	@Override
	public Map<String, Object> searchCriteria(Pair<Gang, Gang> data) {
		return createSearchCriteria("gang_id = ? AND ally_id = ?",
									new Object[]{data.first().getId(), data.second().getId()},
									new int[]{Types.INTEGER, Types.INTEGER}, new int[]{0, 1});
	}
}
