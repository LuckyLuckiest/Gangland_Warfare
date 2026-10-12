package org.luckyraven.gangland.turf.database;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.gangland.turf.powerups.Garrison;

import java.sql.Types;
import java.util.Map;

public class TurfGarrisonTable extends Table<Garrison> {

	public TurfGarrisonTable() {
		super("turf_garrison");

		Attribute<Integer> turfId = new Attribute<>("turf_id", true, Integer.class);
		Attribute<Integer> count  = new Attribute<>("count", false, Integer.class);
		Attribute<Boolean> hold   = new Attribute<>("hold", false, Boolean.class);

		count.setDefaultValue(0);
		hold.setDefaultValue(false);

		this.addAttribute(turfId);
		this.addAttribute(count);
		this.addAttribute(hold);
	}

	@Override
	public Object[] getData(Garrison data) {
		return new Object[]{data.getTurfId(), data.getCount(), data.isHold()};
	}

	@Override
	public Map<String, Object> searchCriteria(Garrison data) {
		return createSearchCriteria("turf_id = ?", new Object[]{data.getTurfId()},
		                            new int[]{Types.INTEGER}, new int[]{0});
	}
}
