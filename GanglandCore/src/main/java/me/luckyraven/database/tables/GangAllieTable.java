package me.luckyraven.database.tables;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.util.Pair;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GangAllieTable extends Table<Pair<Gang, Gang>> {

	public GangAllieTable(GangTable gangTable) {
		super("gang_allie");

		Attribute<Integer> gangId  = new Attribute<>("gang_id", true);
		Attribute<Integer> allieId = new Attribute<>("allie_id", false);
		Attribute<Long>    since   = new Attribute<>("since", false);

		allieId.setUnique(true);

		since.setDefaultValue(-1L);

		gangId.setForeignKey(gangTable.get("gang_id"), gangTable);
		allieId.setForeignKey(gangTable.get("gang_id"), gangTable);

		this.addAttribute(gangId);
		this.addAttribute(allieId);
		this.addAttribute(since);
	}

	@Override
	public Object[] getData(Pair<Gang, Gang> data) {
		return new Object[]{data.first().getId(), data.second().getId()};
	}

	@Override
	public Map<String, Object> searchCriteria(Pair<Gang, Gang> data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "id = ? AND allie_id = ?");
		search.put("info", new Object[]{data.first().getId(), data.second().getId()});
		search.put("type", new int[]{Types.CHAR, Types.CHAR});
		search.put("index", new int[]{0, 1});

		return Collections.unmodifiableMap(search);
	}
}
