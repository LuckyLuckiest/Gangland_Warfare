package me.luckyraven.database.tables.turf;

import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.turf.powerups.Garrison;

import java.sql.Types;
import java.util.Map;

public class TurfGarrisonTable extends Table<Garrison> {

	public TurfGarrisonTable() {
		super("turf_garrison");

		Attribute<Integer> turfId = new Attribute<>("turf_id", true, Integer.class);
		Attribute<Integer> count  = new Attribute<>("count", false, Integer.class);

		count.setDefaultValue(0);

		this.addAttribute(turfId);
		this.addAttribute(count);
	}

	@Override
	public Object[] getData(Garrison data) {
		return new Object[]{data.getTurfId(), data.getCount()};
	}

	@Override
	public Map<String, Object> searchCriteria(Garrison data) {
		return createSearchCriteria("turf_id = ?", new Object[]{data.getTurfId()},
		                            new int[]{Types.INTEGER}, new int[]{0});
	}
}
