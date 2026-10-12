package org.luckyraven.gangland.turf.database;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** Schema for {@code turf_sequence}: a single row holding the turf id high-water mark. */
public class TurfSequenceTable extends Table<TurfSequence> {

	public TurfSequenceTable() {
		super("turf_sequence");

		Attribute<Integer> id        = new Attribute<>("id", true, Integer.class);
		Attribute<Integer> highestId = new Attribute<>("highest_id", false, Integer.class);

		this.addAttribute(id);
		this.addAttribute(highestId);
	}

	@Override
	public Object[] getData(TurfSequence data) {
		return new Object[]{data.id(), data.highestId()};
	}

	@Override
	public Map<String, Object> searchCriteria(TurfSequence data) {
		return createSearchCriteria("id = ?", new Object[]{data.id()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
